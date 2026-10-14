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
import service.exception.AlreadyTakenException;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.CreateGameRequest;
import service.request.JoinGameRequest;
import service.request.ListGamesRequest;
import service.result.CreateGameResult;
import service.result.GameSummary;
import service.result.JoinGameResult;
import service.result.ListGamesResult;
import testutil.FailingAuthDAO;
import testutil.FailingGameDAO;

import java.util.List;

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

    @Test
    public void listGamesReturnsEmptyListWhenNoGamesExist()
            throws DataAccessException, UnauthorizedException {
        addAuthenticatedSession();

        ListGamesResult result = gameService.listGames(
                new ListGamesRequest("valid-token"));

        Assertions.assertNotNull(result.games());
        Assertions.assertTrue(result.games().isEmpty());
    }

    @Test
    public void listGamesReturnsPublicGameSummariesInStorageOrder()
            throws DataAccessException, UnauthorizedException {
        addAuthenticatedSession();
        int firstGameID = gameDAO.createGame(new GameData(
                0,
                "white-player",
                null,
                "First Game",
                new ChessGame()));
        int secondGameID = gameDAO.createGame(new GameData(
                0,
                null,
                "black-player",
                "Second Game",
                new ChessGame()));

        ListGamesResult result = gameService.listGames(
                new ListGamesRequest("valid-token"));

        List<GameSummary> expectedGames = List.of(
                new GameSummary(firstGameID, "white-player", null, "First Game"),
                new GameSummary(secondGameID, null, "black-player", "Second Game"));
        Assertions.assertEquals(expectedGames, result.games());
    }

    @Test
    public void listGamesRejectsMissingAuthToken() {
        ListGamesRequest[] unauthorizedRequests = {
                null,
                new ListGamesRequest(null),
                new ListGamesRequest("   ")
        };

        for (ListGamesRequest request : unauthorizedRequests) {
            Assertions.assertThrows(
                    UnauthorizedException.class,
                    () -> gameService.listGames(request));
        }
    }

    @Test
    public void listGamesRejectsUnknownAuthToken() {
        ListGamesRequest request = new ListGamesRequest("unknown-token");

        Assertions.assertThrows(
                UnauthorizedException.class,
                () -> gameService.listGames(request));
    }

    @Test
    public void listGamesPropagatesAuthenticationStorageFailure() {
        GameService failingService = new GameService(
                new FailingAuthDAO(),
                new MemoryGameDAO());
        ListGamesRequest request = new ListGamesRequest("valid-looking-token");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.listGames(request));

        Assertions.assertEquals("Authentication storage is unavailable", exception.getMessage());
    }

    @Test
    public void listGamesPropagatesGameStorageFailure() throws DataAccessException {
        addAuthenticatedSession();
        GameService failingService = new GameService(authDAO, new FailingGameDAO());
        ListGamesRequest request = new ListGamesRequest("valid-token");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.listGames(request));

        Assertions.assertEquals("Game storage is unavailable", exception.getMessage());
    }

    @Test
    public void joinGameAssignsAuthenticatedUserToWhiteColor()
            throws DataAccessException, BadRequestException,
            UnauthorizedException, AlreadyTakenException {
        addAuthenticatedSession();
        int gameID = addOpenGame("White Game");
        JoinGameRequest request = new JoinGameRequest(
                "valid-token",
                ChessGame.TeamColor.WHITE,
                gameID);

        JoinGameResult result = gameService.joinGame(request);
        GameData updatedGame = gameDAO.getGame(gameID);

        Assertions.assertEquals(new JoinGameResult(), result);
        Assertions.assertEquals("reader", updatedGame.whiteUsername());
        Assertions.assertNull(updatedGame.blackUsername());
        Assertions.assertEquals("White Game", updatedGame.gameName());
        Assertions.assertEquals(new ChessGame(), updatedGame.game());
    }

    @Test
    public void joinGameAssignsAuthenticatedUserToBlackColor()
            throws DataAccessException, BadRequestException,
            UnauthorizedException, AlreadyTakenException {
        addAuthenticatedSession();
        int gameID = addOpenGame("Black Game");
        JoinGameRequest request = new JoinGameRequest(
                "valid-token",
                ChessGame.TeamColor.BLACK,
                gameID);

        gameService.joinGame(request);
        GameData updatedGame = gameDAO.getGame(gameID);

        Assertions.assertNull(updatedGame.whiteUsername());
        Assertions.assertEquals("reader", updatedGame.blackUsername());
    }

    @Test
    public void joinGameAllowsSameUserToClaimBothColors()
            throws DataAccessException, BadRequestException,
            UnauthorizedException, AlreadyTakenException {
        addAuthenticatedSession();
        int gameID = addOpenGame("Solo Game");

        gameService.joinGame(new JoinGameRequest(
                "valid-token",
                ChessGame.TeamColor.WHITE,
                gameID));
        gameService.joinGame(new JoinGameRequest(
                "valid-token",
                ChessGame.TeamColor.BLACK,
                gameID));

        GameData updatedGame = gameDAO.getGame(gameID);
        Assertions.assertEquals("reader", updatedGame.whiteUsername());
        Assertions.assertEquals("reader", updatedGame.blackUsername());
    }

    @Test
    public void joinGameRejectsInvalidRequestFields() throws DataAccessException {
        addAuthenticatedSession();
        int gameID = addOpenGame("Validation Game");
        JoinGameRequest[] invalidRequests = {
                null,
                new JoinGameRequest("valid-token", null, gameID),
                new JoinGameRequest("valid-token", ChessGame.TeamColor.WHITE, null),
                new JoinGameRequest("valid-token", ChessGame.TeamColor.WHITE, 0),
                new JoinGameRequest("valid-token", ChessGame.TeamColor.WHITE, -1)
        };

        for (JoinGameRequest request : invalidRequests) {
            Assertions.assertThrows(
                    BadRequestException.class,
                    () -> gameService.joinGame(request));
        }
    }

    @Test
    public void joinGameRejectsMissingOrUnknownAuthToken() throws DataAccessException {
        int gameID = addOpenGame("Authentication Game");
        JoinGameRequest[] unauthorizedRequests = {
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, gameID),
                new JoinGameRequest("   ", ChessGame.TeamColor.WHITE, gameID),
                new JoinGameRequest("unknown-token", ChessGame.TeamColor.WHITE, gameID)
        };

        for (JoinGameRequest request : unauthorizedRequests) {
            Assertions.assertThrows(
                    UnauthorizedException.class,
                    () -> gameService.joinGame(request));
        }
    }

    @Test
    public void joinGameRejectsUnknownGameIdentifier() throws DataAccessException {
        addAuthenticatedSession();
        JoinGameRequest request = new JoinGameRequest(
                "valid-token",
                ChessGame.TeamColor.WHITE,
                999);

        Assertions.assertThrows(
                BadRequestException.class,
                () -> gameService.joinGame(request));
    }

    @Test
    public void joinGameRejectsOccupiedColor() throws DataAccessException {
        addAuthenticatedSession();
        int gameID = gameDAO.createGame(new GameData(
                0,
                "white-player",
                "black-player",
                "Full Game",
                new ChessGame()));
        JoinGameRequest[] occupiedColorRequests = {
                new JoinGameRequest(
                        "valid-token",
                        ChessGame.TeamColor.WHITE,
                        gameID),
                new JoinGameRequest(
                        "valid-token",
                        ChessGame.TeamColor.BLACK,
                        gameID)
        };

        for (JoinGameRequest request : occupiedColorRequests) {
            Assertions.assertThrows(
                    AlreadyTakenException.class,
                    () -> gameService.joinGame(request));
        }
    }

    @Test
    public void joinGamePropagatesAuthenticationStorageFailure() {
        GameService failingService = new GameService(
                new FailingAuthDAO(),
                new MemoryGameDAO());
        JoinGameRequest request = new JoinGameRequest(
                "valid-looking-token",
                ChessGame.TeamColor.WHITE,
                1);

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.joinGame(request));

        Assertions.assertEquals("Authentication storage is unavailable", exception.getMessage());
    }

    @Test
    public void joinGamePropagatesGameStorageFailure() throws DataAccessException {
        addAuthenticatedSession();
        GameService failingService = new GameService(authDAO, new FailingGameDAO());
        JoinGameRequest request = new JoinGameRequest(
                "valid-token",
                ChessGame.TeamColor.WHITE,
                1);

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> failingService.joinGame(request));

        Assertions.assertEquals("Game storage is unavailable", exception.getMessage());
    }

    private void addAuthenticatedSession() throws DataAccessException {
        authDAO.createAuth(new AuthData("valid-token", "reader"));
    }

    private int addOpenGame(String gameName) throws DataAccessException {
        return gameDAO.createGame(new GameData(
                0,
                null,
                null,
                gameName,
                new ChessGame()));
    }
}
