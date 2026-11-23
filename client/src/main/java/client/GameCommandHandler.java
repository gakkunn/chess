package client;

import chess.ChessGame;
import model.AuthData;
import ui.GameListRenderer;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class GameCommandHandler {

    private static final String INVALID_GAME_NUMBER_MESSAGE =
            "Choose a valid game number from the most recent list. "
                    + "Type 'list' to refresh it.";

    private final ServerFacade serverFacade;
    private final GameplaySession gameplay;
    private final GameListRenderer gameListRenderer;
    private volatile List<GameSummary> displayedGames;

    GameCommandHandler(ServerFacade serverFacade, GameplaySession gameplay) {
        this.serverFacade = Objects.requireNonNull(
                serverFacade,
                "Server facade cannot be null.");
        this.gameplay = gameplay;
        gameListRenderer = new GameListRenderer();
        displayedGames = List.of();
    }

    String evaluate(
            String command,
            String[] tokens,
            AuthData auth) throws ServerFacadeException {
        return switch (command) {
            case "create" -> createGame(tokens, auth.authToken());
            case "list" -> listGames(tokens, auth.authToken());
            case "play" -> playGame(tokens, auth);
            case "observe" -> observeGame(tokens, auth);
            default -> throw new IllegalArgumentException(
                    "Unsupported game command: " + command);
        };
    }

    void clearDisplayedGames() {
        displayedGames = List.of();
    }

    private String createGame(String[] tokens, String authToken)
            throws ServerFacadeException {
        if (tokens.length < 2) {
            return "Usage: create <name>";
        }

        String gameName = String.join(
                " ",
                Arrays.copyOfRange(tokens, 1, tokens.length));

        serverFacade.createGame(authToken, gameName);
        clearDisplayedGames();
        return "Created game '" + gameName + "'.";
    }

    private String listGames(String[] tokens, String authToken)
            throws ServerFacadeException {
        if (tokens.length != 1) {
            return "Usage: list";
        }

        displayedGames = serverFacade.listGames(authToken);
        return gameListRenderer.render(displayedGames);
    }

    private String playGame(String[] tokens, AuthData auth)
            throws ServerFacadeException {
        if (tokens.length != 3) {
            return "Usage: play <number> <WHITE|BLACK>";
        }

        GameSummary selectedGame = findDisplayedGame(tokens[1]);
        if (selectedGame == null) {
            return INVALID_GAME_NUMBER_MESSAGE;
        }

        ChessGame.TeamColor playerColor = findPlayerColor(tokens[2]);
        if (playerColor == null) {
            return "Color must be WHITE or BLACK.";
        }

        if (!claimSeat(auth, selectedGame, playerColor)) {
            return "That color is already taken.";
        }

        clearDisplayedGames();
        return gameplay.connect(auth, selectedGame, playerColor);
    }

    /**
     * Joins the selected color, or keeps the seat when the user already holds it.
     *
     * @return false if another user holds the color
     */
    private boolean claimSeat(AuthData auth, GameSummary game, ChessGame.TeamColor color)
            throws ServerFacadeException {
        String occupant = color == ChessGame.TeamColor.WHITE ? game.whiteUsername() : game.blackUsername();
        if (auth.username().equals(occupant)) {
            return true;
        }

        try {
            serverFacade.joinGame(auth.authToken(), color, game.gameID());
            return true;
        } catch (ServerFacadeException exception) {
            if ("Error: already taken".equals(exception.getMessage())) {
                return false;
            }

            throw exception;
        }
    }

    private String observeGame(String[] tokens, AuthData auth) {
        if (tokens.length != 2) {
            return "Usage: observe <number>";
        }

        GameSummary selectedGame = findDisplayedGame(tokens[1]);
        if (selectedGame == null) {
            return INVALID_GAME_NUMBER_MESSAGE;
        }

        clearDisplayedGames();
        return gameplay.connect(auth, selectedGame, null);
    }

    private GameSummary findDisplayedGame(String gameNumberText) {
        int gameNumber;

        try {
            gameNumber = Integer.parseInt(gameNumberText);
        } catch (NumberFormatException exception) {
            return null;
        }

        int gameIndex = gameNumber - 1;
        List<GameSummary> snapshot = displayedGames;
        if (gameIndex < 0 || gameIndex >= snapshot.size()) {
            return null;
        }

        return snapshot.get(gameIndex);
    }

    private ChessGame.TeamColor findPlayerColor(String colorText) {
        for (ChessGame.TeamColor color : ChessGame.TeamColor.values()) {
            if (color.name().equalsIgnoreCase(colorText)) {
                return color;
            }
        }

        return null;
    }
}
