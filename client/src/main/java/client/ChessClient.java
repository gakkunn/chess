package client;

import model.AuthData;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public class ChessClient implements AutoCloseable {

    private static final String PRELOGIN_HELP = String.join(
            System.lineSeparator(),
            "Available commands:",
            "  register <username> <password> <email> - Create an account",
            "  login <username> <password> - Sign in",
            "  help - Show available commands",
            "  quit - Exit the chess client");

    private static final String POSTLOGIN_HELP = String.join(
            System.lineSeparator(),
            "Available commands:",
            "  create <name> - Create a game",
            "  list - List available games",
            "  play <number> <WHITE|BLACK> - Join a game",
            "  observe <number> - Observe a game",
            "  reconnect - Reconnect to the last game",
            "  logout - Sign out",
            "  help - Show available commands",
            "  quit - Exit the chess client");

    private final ServerFacade serverFacade;
    private final GameCommandHandler gameCommandHandler;
    private final GameplaySession gameplay;
    private final boolean ownsFacade;
    private final Object commandLock = new Object();
    private final Object sessionLock = new Object();
    private volatile Consumer<String> notificationListener = message -> { };
    private ClientState state;
    private AuthData session;

    public ChessClient(String serverUrl) {
        this(new ServerFacade(serverUrl), true);
    }

    public ChessClient(ServerFacade serverFacade) {
        this(serverFacade, false);
    }

    private ChessClient(ServerFacade serverFacade, boolean ownsFacade) {
        this.serverFacade = Objects.requireNonNull(
                serverFacade,
                "Server facade cannot be null.");
        this.ownsFacade = ownsFacade;
        gameplay = new GameplaySession(serverFacade, message -> notificationListener.accept(message),
                this::expireSession, Duration.ofSeconds(5));
        gameCommandHandler = new GameCommandHandler(serverFacade, gameplay);
        state = ClientState.SIGNED_OUT;
    }

    public ClientState getState() {
        synchronized (sessionLock) {
            return state == ClientState.SIGNED_IN ? gameplay.getState() : state;
        }
    }

    public void setNotificationListener(Consumer<String> listener) {
        notificationListener = Objects.requireNonNull(listener, "Notification listener cannot be null.");
    }

    public String evaluate(String input) {
        synchronized (commandLock) {
            return evaluateCommand(input);
        }
    }

    private String evaluateCommand(String input) {
        if (getState() == ClientState.QUIT) {
            return "The chess client is closed.";
        }

        if (input == null || input.isBlank()) {
            return "Please enter a command. Type 'help' to see available commands.";
        }

        String[] tokens = input.trim().split("\\s+");
        String command = tokens[0].toLowerCase(Locale.ROOT);

        if (command.equals("help")) {
            return showHelp(tokens.length);
        }

        if (command.equals("quit")) {
            return quit(tokens.length);
        }

        return switch (getState()) {
            case SIGNED_OUT -> evaluatePreloginCommand(command, tokens);
            case SIGNED_IN -> evaluatePostloginCommand(command, tokens);
            case CONNECTING -> "Waiting for the game board. Type 'help' to see available commands.";
            case IN_GAME -> gameplay.evaluate(command, tokens);
            case QUIT -> "The chess client is closed.";
        };
    }

    private String evaluatePreloginCommand(String command, String[] tokens) {
        return switch (command) {
            case "register" -> register(tokens);
            case "login" -> login(tokens);
            default -> unknownCommand();
        };
    }

    private String evaluatePostloginCommand(String command, String[] tokens) {
        return switch (command) {
            case "logout" -> logout(tokens);
            case "reconnect" -> tokens.length == 1 ? gameplay.reconnect() : "Usage: reconnect";
            case "create", "list", "play", "observe" ->
                    evaluateGameCommand(command, tokens);
            default -> unknownCommand();
        };
    }

    private String evaluateGameCommand(String command, String[] tokens) {
        try {
            AuthData currentSession;
            synchronized (sessionLock) {
                currentSession = session;
            }
            if (currentSession == null) {
                return "Your session is no longer valid. Please sign in again.";
            }
            return gameCommandHandler.evaluate(
                    command,
                    tokens,
                    currentSession);
        } catch (ServerFacadeException exception) {
            return authenticatedUserMessage(exception);
        }
    }

    private String register(String[] tokens) {
        if (tokens.length != 4) {
            return "Usage: register <username> <password> <email>";
        }

        try {
            AuthData authData = serverFacade.register(
                    tokens[1],
                    tokens[2],
                    tokens[3]);
            return beginSession(authData);
        } catch (ServerFacadeException exception) {
            return userMessage(exception);
        }
    }

    private String login(String[] tokens) {
        if (tokens.length != 3) {
            return "Usage: login <username> <password>";
        }

        try {
            AuthData authData = serverFacade.login(tokens[1], tokens[2]);
            return beginSession(authData);
        } catch (ServerFacadeException exception) {
            return userMessage(exception);
        }
    }

    private String beginSession(AuthData authData) {
        gameplay.reset();
        gameCommandHandler.clearDisplayedGames();
        synchronized (sessionLock) {
            session = authData;
            state = ClientState.SIGNED_IN;
        }
        return "Signed in as " + authData.username() + ".";
    }

    private String logout(String[] tokens) {
        if (tokens.length != 1) {
            return "Usage: logout";
        }

        try {
            AuthData currentSession;
            synchronized (sessionLock) {
                currentSession = session;
            }
            if (currentSession == null) {
                return "Your session is no longer valid. Please sign in again.";
            }
            serverFacade.logout(currentSession.authToken());
            clearSession();
            return "Signed out.";
        } catch (ServerFacadeException exception) {
            return authenticatedUserMessage(exception);
        }
    }

    private void clearSession() {
        gameplay.reset();
        gameCommandHandler.clearDisplayedGames();
        synchronized (sessionLock) {
            session = null;
            state = ClientState.SIGNED_OUT;
        }
    }

    private void expireSession(String authToken) {
        synchronized (sessionLock) {
            if (session != null && session.authToken().equals(authToken) && state != ClientState.QUIT) {
                session = null;
                gameCommandHandler.clearDisplayedGames();
                state = ClientState.SIGNED_OUT;
            }
        }
    }

    private String showHelp(int tokenCount) {
        if (tokenCount != 1) {
            return "Usage: help";
        }

        return switch (getState()) {
            case SIGNED_IN -> POSTLOGIN_HELP;
            case CONNECTING -> "Waiting for the game board." + System.lineSeparator()
                    + "  help - Show available commands" + System.lineSeparator() + "  quit - Exit the chess client";
            case IN_GAME -> gameplay.help();
            default -> PRELOGIN_HELP;
        };
    }

    private String quit(int tokenCount) {
        if (tokenCount != 1) {
            return "Usage: quit";
        }

        close();
        return "Goodbye.";
    }

    @Override
    public void close() {
        synchronized (commandLock) {
            synchronized (sessionLock) {
                state = ClientState.QUIT;
                session = null;
            }
            gameplay.reset();
            if (ownsFacade) {
                serverFacade.close();
            }
        }
    }

    private String unknownCommand() {
        return "Unknown command. Type 'help' to see available commands.";
    }

    private String userMessage(ServerFacadeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return "The request could not be completed.";
        }

        return switch (message) {
            case "Error: already taken" -> "That username is already taken.";
            case "Error: unauthorized" ->
                    "The username or password is incorrect.";
            case "Error: bad request" ->
                    "Please check the information and try again.";
            default -> message;
        };
    }

    private String authenticatedUserMessage(
            ServerFacadeException exception) {
        if ("Error: unauthorized".equals(exception.getMessage())) {
            clearSession();
            return "Your session is no longer valid. Please sign in again.";
        }

        return userMessage(exception);
    }
}
