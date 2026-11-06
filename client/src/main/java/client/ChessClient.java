package client;

import chess.ChessGame;
import model.AuthData;
import ui.BoardRenderer;
import ui.GameListRenderer;

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
    private final BoardRenderer boardRenderer;
    private final GameListRenderer gameListRenderer;
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
        boardRenderer = new BoardRenderer();
        gameListRenderer = new GameListRenderer();
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
            case "play" -> playGame(tokens);
            case "observe" -> observeGame(tokens);
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
            return gameListRenderer.render(displayedGames);
        } catch (ServerFacadeException exception) {
            return authenticatedUserMessage(exception);
        }
    }

    private String playGame(String[] tokens) {
        if (tokens.length != 3) {
            return "Usage: play <number> <WHITE|BLACK>";
        }

        GameSummary selectedGame = findDisplayedGame(tokens[1]);
        if (selectedGame == null) {
            return invalidGameNumberMessage();
        }

        ChessGame.TeamColor playerColor = findPlayerColor(tokens[2]);
        if (playerColor == null) {
            return "Color must be WHITE or BLACK.";
        }

        try {
            serverFacade.joinGame(
                    session.authToken(),
                    playerColor,
                    selectedGame.gameID());
            displayedGames = List.of();
            return joinedGameMessage(selectedGame, playerColor);
        } catch (ServerFacadeException exception) {
            if ("Error: already taken".equals(exception.getMessage())) {
                return "That color is already taken.";
            }

            return authenticatedUserMessage(exception);
        }
    }

    private String observeGame(String[] tokens) {
        if (tokens.length != 2) {
            return "Usage: observe <number>";
        }

        GameSummary selectedGame = findDisplayedGame(tokens[1]);
        if (selectedGame == null) {
            return invalidGameNumberMessage();
        }

        ChessGame initialGame = new ChessGame();
        String board = boardRenderer.renderWhite(initialGame.getBoard());

        return "Observing '" + selectedGame.gameName() + "'."
                + System.lineSeparator()
                + board;
    }

    private GameSummary findDisplayedGame(String gameNumberText) {
        int gameNumber;

        try {
            gameNumber = Integer.parseInt(gameNumberText);
        } catch (NumberFormatException exception) {
            return null;
        }

        int gameIndex = gameNumber - 1;
        if (gameIndex < 0 || gameIndex >= displayedGames.size()) {
            return null;
        }

        return displayedGames.get(gameIndex);
    }

    private ChessGame.TeamColor findPlayerColor(String colorText) {
        for (ChessGame.TeamColor color : ChessGame.TeamColor.values()) {
            if (color.name().equalsIgnoreCase(colorText)) {
                return color;
            }
        }

        return null;
    }

    private String joinedGameMessage(
            GameSummary selectedGame,
            ChessGame.TeamColor playerColor) {
        ChessGame initialGame = new ChessGame();
        String board = boardRenderer.render(
                initialGame.getBoard(),
                playerColor);

        return "Joined '" + selectedGame.gameName() + "' as "
                + playerColor + "."
                + System.lineSeparator()
                + board;
    }

    private String invalidGameNumberMessage() {
        return "Choose a valid game number from the most recent list. "
                + "Type 'list' to refresh it.";
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
