package service;

import chess.ChessGame;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import dataaccess.UserDAO;
import model.AuthData;
import model.GameData;
import model.UserData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.request.ClearRequest;
import service.result.ClearResult;
import testutil.FailingAuthDAO;
import testutil.FailingGameDAO;
import testutil.FailingUserDAO;

public class ClearServiceTests {

    private UserDAO userDAO;
    private AuthDAO authDAO;
    private GameDAO gameDAO;
    private ClearService clearService;

    @BeforeEach
    public void setUp() {
        userDAO = new MemoryUserDAO();
        authDAO = new MemoryAuthDAO();
        gameDAO = new MemoryGameDAO();
        clearService = new ClearService(userDAO, authDAO, gameDAO);
    }

    @Test
    public void clearRemovesUsersSessionsAndGames() throws DataAccessException {
        userDAO.createUser(new UserData("reader", "secret-password", "reader@example.com"));
        authDAO.createAuth(new AuthData("token-123", "reader"));
        gameDAO.createGame(new GameData(0, null, null, "Open Game", new ChessGame()));

        ClearResult result = clearService.clear(new ClearRequest());

        Assertions.assertEquals(new ClearResult(), result);
        Assertions.assertNull(userDAO.getUser("reader"));
        Assertions.assertNull(authDAO.getAuth("token-123"));
        Assertions.assertTrue(gameDAO.listGames().isEmpty());
    }

    @Test
    public void clearPropagatesAuthenticationStorageFailure() {
        ClearService failingService = new ClearService(
                new MemoryUserDAO(),
                new FailingAuthDAO(),
                new MemoryGameDAO());

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.clear(new ClearRequest()));

        Assertions.assertEquals("Authentication storage is unavailable", exception.getMessage());
    }

    @Test
    public void clearPropagatesGameStorageFailure() {
        ClearService failingService = new ClearService(
                new MemoryUserDAO(),
                new MemoryAuthDAO(),
                new FailingGameDAO());

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.clear(new ClearRequest()));

        Assertions.assertEquals("Game storage is unavailable", exception.getMessage());
    }

    @Test
    public void clearPropagatesUserStorageFailure() {
        ClearService failingService = new ClearService(
                new FailingUserDAO(),
                new MemoryAuthDAO(),
                new MemoryGameDAO());

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.clear(new ClearRequest()));

        Assertions.assertEquals("User storage is unavailable", exception.getMessage());
    }
}
