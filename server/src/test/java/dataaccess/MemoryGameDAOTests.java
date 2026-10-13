package dataaccess;

import chess.ChessGame;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MemoryGameDAOTests {

    private GameDAO gameDAO;

    @BeforeEach
    public void setUp() {
        gameDAO = new MemoryGameDAO();
    }

    @Test
    public void createGameAssignsPositiveIdentifierAndStoresGame() throws DataAccessException {
        ChessGame chessGame = new ChessGame();
        GameData newGame = new GameData(0, null, null, "First Game", chessGame);

        int gameID = gameDAO.createGame(newGame);
        GameData storedGame = gameDAO.getGame(gameID);

        Assertions.assertTrue(gameID > 0);
        Assertions.assertEquals(gameID, storedGame.gameID());
        Assertions.assertNull(storedGame.whiteUsername());
        Assertions.assertNull(storedGame.blackUsername());
        Assertions.assertEquals("First Game", storedGame.gameName());
        Assertions.assertEquals(chessGame, storedGame.game());
    }

    @Test
    public void createGameAssignsUniqueIdentifiers() throws DataAccessException {
        int firstGameID = gameDAO.createGame(createNewGame("First Game"));
        int secondGameID = gameDAO.createGame(createNewGame("Second Game"));

        Assertions.assertNotEquals(firstGameID, secondGameID);
        Assertions.assertTrue(firstGameID > 0);
        Assertions.assertTrue(secondGameID > 0);
    }

    @Test
    public void getGameReturnsNullForUnknownIdentifier() throws DataAccessException {
        GameData missingGame = gameDAO.getGame(999);

        Assertions.assertNull(missingGame);
    }

    @Test
    public void listGamesReturnsEveryGameInCreationOrder() throws DataAccessException {
        int firstGameID = gameDAO.createGame(createNewGame("First Game"));
        int secondGameID = gameDAO.createGame(createNewGame("Second Game"));

        List<GameData> games = gameDAO.listGames();

        Assertions.assertEquals(2, games.size());
        Assertions.assertEquals(firstGameID, games.get(0).gameID());
        Assertions.assertEquals(secondGameID, games.get(1).gameID());
    }

    @Test
    public void listGamesReturnsIndependentList() throws DataAccessException {
        int gameID = gameDAO.createGame(createNewGame("Independent List"));
        List<GameData> games = gameDAO.listGames();

        games.clear();

        Assertions.assertNotNull(gameDAO.getGame(gameID));
        Assertions.assertEquals(1, gameDAO.listGames().size());
    }

    @Test
    public void updateGameReplacesStoredGame() throws DataAccessException {
        ChessGame chessGame = new ChessGame();
        int gameID = gameDAO.createGame(
                new GameData(0, null, null, "Open Game", chessGame));
        GameData updatedGame = new GameData(
                gameID,
                "white-player",
                null,
                "Open Game",
                chessGame);

        gameDAO.updateGame(updatedGame);

        Assertions.assertEquals(updatedGame, gameDAO.getGame(gameID));
    }

    @Test
    public void updateGameRejectsUnknownIdentifier() {
        GameData missingGame = new GameData(
                999,
                "white-player",
                null,
                "Missing Game",
                new ChessGame());

        Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.updateGame(missingGame));
    }

    @Test
    public void clearRemovesGamesAndResetsIdentifierSequence() throws DataAccessException {
        int removedGameID = gameDAO.createGame(createNewGame("Removed Game"));

        gameDAO.clear();

        Assertions.assertNull(gameDAO.getGame(removedGameID));
        Assertions.assertTrue(gameDAO.listGames().isEmpty());

        int newGameID = gameDAO.createGame(createNewGame("New Game"));

        Assertions.assertEquals(1, newGameID);
    }

    @Test
    public void createGameRejectsMissingGameData() {
        Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.createGame(null));
    }

    @Test
    public void updateGameRejectsMissingGameData() {
        Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.updateGame(null));
    }

    private GameData createNewGame(String gameName) {
        return new GameData(0, null, null, gameName, new ChessGame());
    }
}
