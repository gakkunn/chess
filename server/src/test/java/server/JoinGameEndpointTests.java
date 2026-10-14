package server;

import chess.ChessGame;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import service.request.JoinGameRequest;
import service.request.RegisterRequest;
import service.result.CreateGameResult;
import service.result.ErrorResult;
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

public class JoinGameEndpointTests {

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
    public void joinGameAssignsAuthenticatedUserToRequestedColor()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(
                serverUri,
                "reader",
                "reader@example.com");
        CreateGameResult createdGame = createGame(
                serverUri,
                registeredUser.authToken(),
                "Evening Match");

        HttpResponse<String> response = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(
                        null,
                        ChessGame.TeamColor.WHITE,
                        createdGame.gameID()));
        JsonObject responseBody = serializer.fromJson(response.body(), JsonObject.class);
        ListGamesResult listedGames = listGames(serverUri, registeredUser.authToken());

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(responseBody.isEmpty());
        Assertions.assertEquals("reader", listedGames.games().getFirst().whiteUsername());
        Assertions.assertNull(listedGames.games().getFirst().blackUsername());
    }

    @Test
    public void joinGameAllowsSameUserToClaimBothColors()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(
                serverUri,
                "reader",
                "reader@example.com");
        CreateGameResult createdGame = createGame(
                serverUri,
                registeredUser.authToken(),
                "Solo Match");

        HttpResponse<String> whiteResponse = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(
                        null,
                        ChessGame.TeamColor.WHITE,
                        createdGame.gameID()));
        HttpResponse<String> blackResponse = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(
                        null,
                        ChessGame.TeamColor.BLACK,
                        createdGame.gameID()));

        ListGamesResult listedGames = listGames(serverUri, registeredUser.authToken());
        Assertions.assertEquals(200, whiteResponse.statusCode());
        Assertions.assertEquals(200, blackResponse.statusCode());
        Assertions.assertEquals("reader", listedGames.games().getFirst().whiteUsername());
        Assertions.assertEquals("reader", listedGames.games().getFirst().blackUsername());
    }

    @Test
    public void joinGameRejectsMissingPlayerColor()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(
                serverUri,
                "reader",
                "reader@example.com");
        CreateGameResult createdGame = createGame(
                serverUri,
                registeredUser.authToken(),
                "Evening Match");

        HttpResponse<String> response = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(null, null, createdGame.gameID()));
        assertErrorResponse(response, 400, "Error: bad request");
    }

    @Test
    public void joinGameRejectsMissingGameIdentifier()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(
                serverUri,
                "reader",
                "reader@example.com");

        HttpResponse<String> response = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, null));
        assertErrorResponse(response, 400, "Error: bad request");
    }

    @Test
    public void joinGameRejectsUnknownGameIdentifier()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(
                serverUri,
                "reader",
                "reader@example.com");

        HttpResponse<String> response = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, 999));
        assertErrorResponse(response, 400, "Error: bad request");
    }

    @Test
    public void joinGameRejectsMissingAuthToken()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = joinGame(
                serverUri,
                null,
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, 1));
        assertErrorResponse(response, 401, "Error: unauthorized");
    }

    @Test
    public void joinGameRejectsOccupiedColor()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult firstUser = registerUser(
                serverUri,
                "first-user",
                "first@example.com");
        RegisterResult secondUser = registerUser(
                serverUri,
                "second-user",
                "second@example.com");
        CreateGameResult createdGame = createGame(
                serverUri,
                firstUser.authToken(),
                "Contested Match");
        JoinGameRequest request = new JoinGameRequest(
                null,
                ChessGame.TeamColor.BLACK,
                createdGame.gameID());
        joinGame(serverUri, firstUser.authToken(), request);

        HttpResponse<String> response = joinGame(
                serverUri,
                secondUser.authToken(),
                request);
        assertErrorResponse(response, 403, "Error: already taken");
    }

    @Test
    public void joinGameAuthenticationFailureReturnsInternalServerError()
            throws IOException, InterruptedException {
        Server server = new Server(
                new MemoryUserDAO(),
                new FailingAuthDAO(),
                new MemoryGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = joinGame(
                serverUri,
                "valid-looking-token",
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, 1));
        assertErrorResponse(
                response,
                500,
                "Error: Authentication storage is unavailable");
    }

    @Test
    public void joinGameStorageFailureReturnsInternalServerError()
            throws DataAccessException, IOException, InterruptedException {
        AuthDAO authDAO = new MemoryAuthDAO();
        authDAO.createAuth(new AuthData("valid-token", "reader"));
        Server server = new Server(
                new MemoryUserDAO(),
                authDAO,
                new FailingGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = joinGame(
                serverUri,
                "valid-token",
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, 1));
        assertErrorResponse(response, 500, "Error: Game storage is unavailable");
    }

    private URI startServer(Server server) {
        int port = server.run(0);
        startedServers.add(server);
        return URI.create("http://localhost:" + port + "/");
    }

    private RegisterResult registerUser(
            URI serverUri,
            String username,
            String email) throws IOException, InterruptedException {
        RegisterRequest request = new RegisterRequest(
                username,
                "secret-password",
                email);
        HttpResponse<String> response = sendJsonRequest(
                serverUri.resolve("user"),
                "POST",
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
        HttpResponse<String> response = sendJsonRequest(
                serverUri.resolve("game"),
                "POST",
                serializer.toJson(request),
                authToken);

        Assertions.assertEquals(200, response.statusCode());
        return serializer.fromJson(response.body(), CreateGameResult.class);
    }

    private HttpResponse<String> joinGame(
            URI serverUri,
            String authToken,
            JoinGameRequest request) throws IOException, InterruptedException {
        return sendJsonRequest(
                serverUri.resolve("game"),
                "PUT",
                serializer.toJson(request),
                authToken);
    }

    private ListGamesResult listGames(URI serverUri, String authToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(serverUri.resolve("game"))
                .header("authorization", authToken)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        Assertions.assertEquals(200, response.statusCode());
        return serializer.fromJson(response.body(), ListGamesResult.class);
    }

    private HttpResponse<String> sendJsonRequest(
            URI endpoint,
            String method,
            String requestBody,
            String authToken) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(requestBody));

        if (authToken != null) {
            requestBuilder.header("authorization", authToken);
        }

        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertErrorResponse(
            HttpResponse<String> response,
            int expectedStatus,
            String expectedMessage) {
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(expectedStatus, response.statusCode());
        Assertions.assertEquals(expectedMessage, result.message());
    }
}
