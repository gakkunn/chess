package client;

import model.AuthData;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChessClient {

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
            "  logout - Sign out",
            "  help - Show available commands",
            "  quit - Exit the chess client");

    private final ServerFacade serverFacade;
    private ClientState state;
    private AuthData session;
    private List<GameSummary> displayedGames;

    public ChessClient(String serverUrl) {
        this(new ServerFacade(serverUrl));
    }

    public ChessClient(ServerFacade serverFacade) {
        this.serverFacade = Objects.requireNonNull(
                serverFacade,
                "Server facade cannot be null.");
        state = ClientState.SIGNED_OUT;
        displayedGames = List.of();
    }

    public ClientState getState() {
        return state;
    }

    public String evaluate(String input) {
        if (state == ClientState.QUIT) {
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

        return switch (state) {
            case SIGNED_OUT -> evaluatePreloginCommand(command, tokens);
            case SIGNED_IN -> evaluatePostloginCommand(command, tokens);
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
            case "create" -> createGame(tokens);
            case "list" -> listGames(tokens);
            default -> unknownCommand();
        };
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
        session = authData;
        displayedGames = List.of();
        state = ClientState.SIGNED_IN;
        return "Signed in as " + authData.username() + ".";
    }

    private String logout(String[] tokens) {
        if (tokens.length != 1) {
            return "Usage: logout";
        }

        try {
            serverFacade.logout(session.authToken());
            clearSession();
            return "Signed out.";
        } catch (ServerFacadeException exception) {
            return authenticatedUserMessage(exception);
        }
    }

    private String createGame(String[] tokens) {
        if (tokens.length < 2) {
            return "Usage: create <name>";
        }

        String gameName = String.join(
                " ",
                Arrays.copyOfRange(tokens, 1, tokens.length));

        try {
            serverFacade.createGame(session.authToken(), gameName);
            displayedGames = List.of();
            return "Created game '" + gameName + "'.";
        } catch (ServerFacadeException exception) {
            return authenticatedUserMessage(exception);
        }
    }

    private String listGames(String[] tokens) {
        if (tokens.length != 1) {
            return "Usage: list";
        }

        try {
            displayedGames = serverFacade.listGames(session.authToken());
            return formatGameList(displayedGames);
        } catch (ServerFacadeException exception) {
            return authenticatedUserMessage(exception);
        }
    }

    private String formatGameList(List<GameSummary> games) {
        if (games.isEmpty()) {
            return "No games are available.";
        }

        StringBuilder response = new StringBuilder("Available games:");

        for (int index = 0; index < games.size(); index++) {
            GameSummary game = games.get(index);
            response.append(System.lineSeparator())
                    .append("  ")
                    .append(index + 1)
                    .append(". ")
                    .append(game.gameName())
                    .append(" | White: ")
                    .append(playerName(game.whiteUsername()))
                    .append(" | Black: ")
                    .append(playerName(game.blackUsername()));
        }

        return response.toString();
    }

    private String playerName(String username) {
        if (username == null || username.isBlank()) {
            return "Open";
        }

        return username;
    }

    private void clearSession() {
        session = null;
        displayedGames = List.of();
        state = ClientState.SIGNED_OUT;
    }

    private String showHelp(int tokenCount) {
        if (tokenCount != 1) {
            return "Usage: help";
        }

        if (state == ClientState.SIGNED_IN) {
            return POSTLOGIN_HELP;
        }

        return PRELOGIN_HELP;
    }

    private String quit(int tokenCount) {
        if (tokenCount != 1) {
            return "Usage: quit";
        }

        state = ClientState.QUIT;
        return "Goodbye.";
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
