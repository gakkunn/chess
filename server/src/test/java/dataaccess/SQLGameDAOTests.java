package dataaccess;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import chess.InvalidMoveException;
import dataaccess.sql.SQLGameDAO;
import dataaccess.sql.SQLUserDAO;
import model.GameData;
import model.UserData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class SQLGameDAOTests extends SQLDAOTestSupportTests {

    private GameDAO gameDAO;

    @BeforeEach
    public void setUpGameDAO() {
        gameDAO = new SQLGameDAO();
    }

    @Test
    public void createGameAssignsIdentifierAndPersistsGameAcrossDAOInstances()
            throws DataAccessException {
        GameData newGame = createNewGame("Persistent Game");

        int gameID = gameDAO.createGame(newGame);
        GameData storedGame = new SQLGameDAO().getGame(gameID);

        Assertions.assertTrue(gameID > 0);
        Assertions.assertEquals(gameID, storedGame.gameID());
        Assertions.assertNull(storedGame.whiteUsername());
        Assertions.assertNull(storedGame.blackUsername());
        Assertions.assertEquals(newGame.gameName(), storedGame.gameName());
        Assertions.assertEquals(newGame.game(), storedGame.game());
    }

    @Test
    public void createGameAssignsUniqueIdentifiersInCreationOrder()
            throws DataAccessException {
        int firstGameID = gameDAO.createGame(createNewGame("First Game"));
        int secondGameID = gameDAO.createGame(createNewGame("Second Game"));

        Assertions.assertEquals(1, firstGameID);
        Assertions.assertEquals(2, secondGameID);
    }

    @Test
    public void listGamesReturnsEveryGameInCreationOrder()
            throws DataAccessException {
        int firstGameID = gameDAO.createGame(createNewGame("First Game"));
        int secondGameID = gameDAO.createGame(createNewGame("Second Game"));

        List<GameData> games = gameDAO.listGames();

        Assertions.assertEquals(2, games.size());
        Assertions.assertEquals(firstGameID, games.get(0).gameID());
        Assertions.assertEquals("First Game", games.get(0).gameName());
        Assertions.assertEquals(secondGameID, games.get(1).gameID());
        Assertions.assertEquals("Second Game", games.get(1).gameName());
    }

    @Test
    public void listGamesReturnsEmptyListWhenNoGamesAreStored()
            throws DataAccessException {
        List<GameData> games = gameDAO.listGames();

        Assertions.assertNotNull(games);
        Assertions.assertTrue(games.isEmpty());
    }

    @Test
    public void getGameReturnsNullForUnknownIdentifier() throws DataAccessException {
        GameData missingGame = gameDAO.getGame(999);

        Assertions.assertNull(missingGame);
    }

    @Test
    public void updateGamePersistsPlayersAndCurrentChessState()
            throws DataAccessException, InvalidMoveException {
        storeUser("white-player");
        storeUser("black-player");
        int gameID = gameDAO.createGame(createNewGame("Updated Game"));
        ChessGame chessGame = new ChessGame();
        chessGame.makeMove(new ChessMove(
                new ChessPosition(2, 5),
                new ChessPosition(4, 5),
                null));
        GameData updatedGame = new GameData(
                gameID,
                "white-player",
                "black-player",
                "Updated Game",
                chessGame);

        gameDAO.updateGame(updatedGame);
        GameData storedGame = new SQLGameDAO().getGame(gameID);

        Assertions.assertEquals(updatedGame, storedGame);
        Assertions.assertEquals(
                ChessGame.TeamColor.BLACK,
                storedGame.game().getTeamTurn());
    }

    @Test
    public void updateGameRejectsUnknownIdentifier() {
        GameData missingGame = new GameData(
                999,
                null,
                null,
                "Missing Game",
                new ChessGame());

        Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.updateGame(missingGame));
    }

    @Test
    public void updateGameRejectsUnknownPlayerWithoutChangingStoredGame()
            throws DataAccessException {
        int gameID = gameDAO.createGame(createNewGame("Open Game"));
        GameData invalidUpdate = new GameData(
                gameID,
                "missing-user",
                null,
                "Open Game",
                new ChessGame());

        Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.updateGame(invalidUpdate));
        Assertions.assertNull(gameDAO.getGame(gameID).whiteUsername());
    }

    @Test
    public void clearRemovesGamesAndResetsIdentifierSequence()
            throws DataAccessException {
        int removedGameID = gameDAO.createGame(createNewGame("Removed Game"));

        gameDAO.clear();

        Assertions.assertNull(gameDAO.getGame(removedGameID));
        Assertions.assertTrue(gameDAO.listGames().isEmpty());
        Assertions.assertEquals(
                1,
                gameDAO.createGame(createNewGame("New Game")));
    }

    @Test
    public void createGameRejectsMissingGameData() {
        Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.createGame(null));
    }

    @Test
    public void createGameRejectsMissingRequiredFields() {
        GameData[] incompleteGames = {
                new GameData(0, null, null, null, new ChessGame()),
                new GameData(0, null, null, "Missing State", null)
        };

        for (GameData incompleteGame : incompleteGames) {
            Assertions.assertThrows(
                    DataAccessException.class,
                    () -> gameDAO.createGame(incompleteGame));
        }
    }

    @Test
    public void createGameRejectsUnknownPlayer() {
        GameData gameWithUnknownPlayer = new GameData(
                0,
                "missing-user",
                null,
                "Invalid Player",
                new ChessGame());

        Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.createGame(gameWithUnknownPlayer));
    }

    @Test
    public void updateGameRejectsMissingOrInvalidGameData() {
        GameData[] invalidGames = {
                new GameData(0, null, null, "Missing Identifier", new ChessGame()),
                new GameData(1, null, null, null, new ChessGame()),
                new GameData(1, null, null, "Missing State", null)
        };

        Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.updateGame(null));
        for (GameData invalidGame : invalidGames) {
            Assertions.assertThrows(
                    DataAccessException.class,
                    () -> gameDAO.updateGame(invalidGame));
        }
    }

    @Test
    public void getGameReportsInvalidStoredChessGame()
            throws DataAccessException, SQLException {
        int gameID = insertGameWithJson("Invalid JSON", "not-json");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> gameDAO.getGame(gameID));

        Assertions.assertEquals(
                "Chess game JSON must contain an object",
                exception.getMessage());
    }

    private GameData createNewGame(String gameName) {
        return new GameData(0, null, null, gameName, new ChessGame());
    }

    private void storeUser(String username) throws DataAccessException {
        UserData user = new UserData(
                username,
                "secret-password",
                username + "@example.com");
        new SQLUserDAO().createUser(user);
    }

    private int insertGameWithJson(String gameName, String gameJson)
            throws DataAccessException, SQLException {
        String sql = """
                INSERT INTO games (game_name, game_json)
                VALUES (?, ?)
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, gameName);
            statement.setString(2, gameJson);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                Assertions.assertTrue(generatedKeys.next());
                return generatedKeys.getInt(1);
            }
        }
    }
}
