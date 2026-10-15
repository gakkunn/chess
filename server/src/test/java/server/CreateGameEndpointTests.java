package server;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import model.AuthData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.CreateGameRequest;
import service.result.CreateGameResult;
import service.result.ErrorResult;
import service.result.RegisterResult;
import testutil.FailingAuthDAO;
import testutil.FailingGameDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;

public class CreateGameEndpointTests extends EndpointTestSupport {

    @Test
    public void createGameReturnsPositiveGameIdentifier()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");

        HttpResponse<String> response = createGame(
                serverUri,
                registeredUser.authToken(),
                "Evening Match");
        CreateGameResult result = serializer.fromJson(response.body(), CreateGameResult.class);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(result.gameID() > 0);
    }

    @Test
    public void createGameRejectsMissingGameName()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");

        HttpResponse<String> response = createGame(
                serverUri,
                registeredUser.authToken(),
                null);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(400, response.statusCode());
        Assertions.assertEquals("Error: bad request", result.message());
    }

    @Test
    public void createGameRejectsMissingAuthToken()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = createGame(
                serverUri,
                null,
                "Evening Match");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void createGameRejectsUnknownAuthToken()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = createGame(
                serverUri,
                "unknown-token",
                "Evening Match");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void createGameAuthenticationFailureReturnsInternalServerError()
            throws IOException, InterruptedException {
        Server server = new Server(
                new MemoryUserDAO(),
                new FailingAuthDAO(),
                new MemoryGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = createGame(
                serverUri,
                "valid-looking-token",
                "Evening Match");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: Authentication storage is unavailable", result.message());
    }

    @Test
    public void createGameStorageFailureReturnsInternalServerError()
            throws DataAccessException, IOException, InterruptedException {
        AuthDAO authDAO = new MemoryAuthDAO();
        authDAO.createAuth(new AuthData("valid-token", "reader"));
        Server server = new Server(
                new MemoryUserDAO(),
                authDAO,
                new FailingGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = createGame(
                serverUri,
                "valid-token",
                "Evening Match");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: Game storage is unavailable", result.message());
    }

    private HttpResponse<String> createGame(
            URI serverUri,
            String authToken,
            String gameName) throws IOException, InterruptedException {
        CreateGameRequest request = new CreateGameRequest(null, gameName);
        return sendRequest(
                serverUri.resolve("game"),
                "POST",
                serializer.toJson(request),
                authToken);
    }
}
