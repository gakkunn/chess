package server;

import com.google.gson.Gson;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import model.AuthData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.CreateGameRequest;
import service.request.RegisterRequest;
import service.result.CreateGameResult;
import service.result.ErrorResult;
import service.result.GameSummary;
import service.result.ListGamesResult;
import service.result.RegisterResult;
import testutil.FailingAuthDAO;
import testutil.FailingGameDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ListGamesEndpointTests {

    private final List<Server> startedServers = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson serializer = new Gson();

    @AfterEach
    public void stopServers() {
        for (Server server : startedServers) {
            server.stop();
        }

        httpClient.close();
    }

    @Test
    public void listGamesReturnsEmptyListWhenNoGamesExist()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(serverUri);

        HttpResponse<String> response = listGames(
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
        RegisterResult registeredUser = registerUser(serverUri);
        CreateGameResult firstGame = createGame(
                serverUri,
                registeredUser.authToken(),
                "First Game");
        CreateGameResult secondGame = createGame(
                serverUri,
                registeredUser.authToken(),
                "Second Game");

        HttpResponse<String> response = listGames(
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

        HttpResponse<String> response = listGames(serverUri, null);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void listGamesRejectsUnknownAuthToken()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = listGames(serverUri, "unknown-token");
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

        HttpResponse<String> response = listGames(serverUri, "valid-looking-token");
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

        HttpResponse<String> response = listGames(serverUri, "valid-token");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: Game storage is unavailable", result.message());
    }

    private URI startServer(Server server) {
        int port = server.run(0);
        startedServers.add(server);
        return URI.create("http://localhost:" + port + "/");
    }

    private RegisterResult registerUser(URI serverUri) throws IOException, InterruptedException {
        RegisterRequest request = new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com");
        HttpResponse<String> response = post(
                serverUri.resolve("user"),
                serializer.toJson(request),
                null);

        Assertions.assertEquals(200, response.statusCode());
        return serializer.fromJson(response.body(), RegisterResult.class);
    }

    private CreateGameResult createGame(
            URI serverUri,
            String authToken,
            String gameName) throws IOException, InterruptedException {
        CreateGameRequest request = new CreateGameRequest(null, gameName);
        HttpResponse<String> response = post(
                serverUri.resolve("game"),
                serializer.toJson(request),
                authToken);

        Assertions.assertEquals(200, response.statusCode());
        return serializer.fromJson(response.body(), CreateGameResult.class);
    }

    private HttpResponse<String> listGames(URI serverUri, String authToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(serverUri.resolve("game"))
                .GET();

        if (authToken != null) {
            requestBuilder.header("authorization", authToken);
        }

        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(
            URI endpoint,
            String requestBody,
            String authToken) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (authToken != null) {
            requestBuilder.header("authorization", authToken);
        }

        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
