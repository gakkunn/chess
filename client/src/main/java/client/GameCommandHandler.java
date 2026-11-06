package client;

import chess.ChessGame;
import ui.BoardRenderer;
import ui.GameListRenderer;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class GameCommandHandler {

    private static final String INVALID_GAME_NUMBER_MESSAGE =
            "Choose a valid game number from the most recent list. "
                    + "Type 'list' to refresh it.";

    private final ServerFacade serverFacade;
    private final BoardRenderer boardRenderer;
    private final GameListRenderer gameListRenderer;
    private List<GameSummary> displayedGames;

    GameCommandHandler(ServerFacade serverFacade) {
        this.serverFacade = Objects.requireNonNull(
                serverFacade,
                "Server facade cannot be null.");
        boardRenderer = new BoardRenderer();
        gameListRenderer = new GameListRenderer();
        displayedGames = List.of();
    }

    String evaluate(
            String command,
            String[] tokens,
            String authToken) throws ServerFacadeException {
        return switch (command) {
            case "create" -> createGame(tokens, authToken);
            case "list" -> listGames(tokens, authToken);
            case "play" -> playGame(tokens, authToken);
            case "observe" -> observeGame(tokens);
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

    private String playGame(String[] tokens, String authToken)
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

        try {
            serverFacade.joinGame(
                    authToken,
                    playerColor,
                    selectedGame.gameID());
        } catch (ServerFacadeException exception) {
            if ("Error: already taken".equals(exception.getMessage())) {
                return "That color is already taken.";
            }

            throw exception;
        }

        clearDisplayedGames();
        return joinedGameMessage(selectedGame, playerColor);
    }

    private String observeGame(String[] tokens) {
        if (tokens.length != 2) {
            return "Usage: observe <number>";
        }

        GameSummary selectedGame = findDisplayedGame(tokens[1]);
        if (selectedGame == null) {
            return INVALID_GAME_NUMBER_MESSAGE;
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
}
