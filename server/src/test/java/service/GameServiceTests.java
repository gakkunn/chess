package service;

import chess.ChessGame;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.CreateGameRequest;
import service.result.CreateGameResult;
import testutil.FailingAuthDAO;
import testutil.FailingGameDAO;

public class GameServiceTests {

    private AuthDAO authDAO;
    private GameDAO gameDAO;
    private GameService gameService;

    @BeforeEach
    public void setUp() {
        authDAO = new MemoryAuthDAO();
        gameDAO = new MemoryGameDAO();
        gameService = new GameService(authDAO, gameDAO);
    }

    @Test
    public void createGameStoresInitializedUnclaimedGame()
            throws DataAccessException, BadRequestException, UnauthorizedException {
        addAuthenticatedSession();
        CreateGameRequest request = new CreateGameRequest(
                "valid-token",
                "Evening Match");

        CreateGameResult result = gameService.createGame(request);
        GameData storedGame = gameDAO.getGame(result.gameID());

        Assertions.assertTrue(result.gameID() > 0);
        Assertions.assertEquals(result.gameID(), storedGame.gameID());
        Assertions.assertNull(storedGame.whiteUsername());
        Assertions.assertNull(storedGame.blackUsername());
        Assertions.assertEquals("Evening Match", storedGame.gameName());
        Assertions.assertEquals(new ChessGame(), storedGame.game());
    }

    @Test
    public void createGameRejectsMissingGameName() throws DataAccessException {
        addAuthenticatedSession();
        CreateGameRequest[] invalidRequests = {
                null,
                new CreateGameRequest("valid-token", null),
                new CreateGameRequest("valid-token", "   ")
        };

        for (CreateGameRequest request : invalidRequests) {
            Assertions.assertThrows(
                    BadRequestException.class,
                    () -> gameService.createGame(request));
        }
    }

    @Test
    public void createGameRejectsMissingAuthToken() {
        CreateGameRequest[] unauthorizedRequests = {
                new CreateGameRequest(null, "Evening Match"),
                new CreateGameRequest("   ", "Evening Match")
        };

        for (CreateGameRequest request : unauthorizedRequests) {
            Assertions.assertThrows(
                    UnauthorizedException.class,
                    () -> gameService.createGame(request));
        }
    }

    @Test
    public void createGameRejectsUnknownAuthToken() {
        CreateGameRequest request = new CreateGameRequest(
                "unknown-token",
                "Evening Match");

        Assertions.assertThrows(
                UnauthorizedException.class,
                () -> gameService.createGame(request));
    }

    @Test
    public void createGamePropagatesAuthenticationStorageFailure() {
        GameService failingService = new GameService(
                new FailingAuthDAO(),
                new MemoryGameDAO());
        CreateGameRequest request = new CreateGameRequest(
                "valid-looking-token",
                "Evening Match");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.createGame(request));

        Assertions.assertEquals("Authentication storage is unavailable", exception.getMessage());
    }

    @Test
    public void createGamePropagatesGameStorageFailure() throws DataAccessException {
        addAuthenticatedSession();
        GameService failingService = new GameService(authDAO, new FailingGameDAO());
        CreateGameRequest request = new CreateGameRequest(
                "valid-token",
                "Evening Match");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.createGame(request));

        Assertions.assertEquals("Game storage is unavailable", exception.getMessage());
    }

    private void addAuthenticatedSession() throws DataAccessException {
        authDAO.createAuth(new AuthData("valid-token", "reader"));
    }
}
