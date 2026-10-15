package server;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import model.AuthData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.result.CreateGameResult;
import service.result.ErrorResult;
import service.result.GameSummary;
import service.result.ListGamesResult;
import service.result.RegisterResult;
import testutil.FailingAuthDAO;
import testutil.FailingGameDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;

public class ListGamesEndpointTests extends EndpointTestSupport {

    @Test
    public void listGamesReturnsEmptyListWhenNoGamesExist()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");

        HttpResponse<String> response = requestGames(
                serverUri,
                registeredUser.authToken());
        ListGamesResult result = serializer.fromJson(response.body(), ListGamesResult.class);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertNotNull(result.games());
        Assertions.assertTrue(result.games().isEmpty());
    }

    @Test
    public void listGamesReturnsCreatedGames()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");
        CreateGameResult firstGame = createGameSuccessfully(
                serverUri,
                registeredUser.authToken(),
                "First Game");
        CreateGameResult secondGame = createGameSuccessfully(
                serverUri,
                registeredUser.authToken(),
                "Second Game");

        HttpResponse<String> response = requestGames(
                serverUri,
                registeredUser.authToken());
        ListGamesResult result = serializer.fromJson(response.body(), ListGamesResult.class);

        List<GameSummary> expectedGames = List.of(
                new GameSummary(firstGame.gameID(), null, null, "First Game"),
                new GameSummary(secondGame.gameID(), null, null, "Second Game"));
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals(expectedGames, result.games());
    }

    @Test
    public void listGamesRejectsMissingAuthToken()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = requestGames(serverUri, null);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void listGamesRejectsUnknownAuthToken()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = requestGames(serverUri, "unknown-token");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void listGamesAuthenticationFailureReturnsInternalServerError()
            throws IOException, InterruptedException {
        Server server = new Server(
                new MemoryUserDAO(),
                new FailingAuthDAO(),
                new MemoryGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = requestGames(serverUri, "valid-looking-token");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: Authentication storage is unavailable", result.message());
    }

    @Test
    public void listGamesStorageFailureReturnsInternalServerError()
            throws DataAccessException, IOException, InterruptedException {
        AuthDAO authDAO = new MemoryAuthDAO();
        authDAO.createAuth(new AuthData("valid-token", "reader"));
        Server server = new Server(
                new MemoryUserDAO(),
                authDAO,
                new FailingGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = requestGames(serverUri, "valid-token");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: Game storage is unavailable", result.message());
    }

}
