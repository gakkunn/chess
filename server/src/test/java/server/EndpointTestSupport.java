package server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dataaccess.DataAccessException;
import dataaccess.sql.SQLAuthDAO;
import dataaccess.sql.SQLDatabaseInitializer;
import dataaccess.sql.SQLGameDAO;
import dataaccess.sql.SQLUserDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import service.request.CreateGameRequest;
import service.request.LoginRequest;
import service.request.RegisterRequest;
import service.result.CreateGameResult;
import service.result.ErrorResult;
import service.result.ListGamesResult;
import service.result.LoginResult;
import service.result.RegisterResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides reusable server lifecycle, HTTP, and assertion helpers for endpoint tests.
 */
abstract class EndpointTestSupport {

    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String DEFAULT_PASSWORD = "secret-password";

    protected final Gson serializer = new Gson();

    private final List<Server> startedServers = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    public void resetPersistentDatabase() throws DataAccessException {
        SQLDatabaseInitializer.initialize();
        new SQLAuthDAO().clear();
        new SQLGameDAO().clear();
        new SQLUserDAO().clear();
    }

    @AfterEach
    public void stopServers() {
        for (Server server : startedServers) {
            server.stop();
        }

        httpClient.close();
    }

    protected URI startServer(Server server) {
        int port = server.run(0);
        startedServers.add(server);
        return URI.create("http://localhost:" + port + "/");
    }

    protected void stopServer(Server server) {
        server.stop();
        startedServers.remove(server);
    }

    protected HttpResponse<String> sendRequest(
            URI endpoint,
            String method,
            String requestBody,
            String authToken) throws IOException, InterruptedException {
        return sendRequest(
                endpoint,
                method,
                requestBody,
                authToken,
                "authorization");
    }

    protected HttpResponse<String> sendRequest(
            URI endpoint,
            String method,
            String requestBody,
            String authToken,
            String authHeaderName) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher bodyPublisher = requestBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(requestBody);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                .method(method, bodyPublisher);

        if (requestBody != null) {
            requestBuilder.header("Content-Type", JSON_CONTENT_TYPE);
        }

        if (authToken != null) {
            requestBuilder.header(authHeaderName, authToken);
        }

        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected RegisterResult registerSuccessfully(
            URI serverUri,
            String username,
            String email) throws IOException, InterruptedException {
        RegisterRequest request = new RegisterRequest(
                username,
                DEFAULT_PASSWORD,
                email);
        HttpResponse<String> response = sendRequest(
                serverUri.resolve("user"),
                "POST",
                serializer.toJson(request),
                null);

        Assertions.assertEquals(200, response.statusCode());
        assertJsonContentType(response);
        return serializer.fromJson(response.body(), RegisterResult.class);
    }

    protected LoginResult loginSuccessfully(URI serverUri, String username)
            throws IOException, InterruptedException {
        LoginRequest request = new LoginRequest(username, DEFAULT_PASSWORD);
        HttpResponse<String> response = sendRequest(
                serverUri.resolve("session"),
                "POST",
                serializer.toJson(request),
                null);

        Assertions.assertEquals(200, response.statusCode());
        assertJsonContentType(response);
        return serializer.fromJson(response.body(), LoginResult.class);
    }

    protected CreateGameResult createGameSuccessfully(
            URI serverUri,
            String authToken,
            String gameName) throws IOException, InterruptedException {
        CreateGameRequest request = new CreateGameRequest(null, gameName);
        HttpResponse<String> response = sendRequest(
                serverUri.resolve("game"),
                "POST",
                serializer.toJson(request),
                authToken);

        Assertions.assertEquals(200, response.statusCode());
        assertJsonContentType(response);
        return serializer.fromJson(response.body(), CreateGameResult.class);
    }

    protected HttpResponse<String> requestGames(URI serverUri, String authToken)
            throws IOException, InterruptedException {
        return sendRequest(
                serverUri.resolve("game"),
                "GET",
                null,
                authToken);
    }

    protected ListGamesResult listGamesSuccessfully(URI serverUri, String authToken)
            throws IOException, InterruptedException {
        HttpResponse<String> response = requestGames(serverUri, authToken);

        Assertions.assertEquals(200, response.statusCode());
        assertJsonContentType(response);
        return serializer.fromJson(response.body(), ListGamesResult.class);
    }

    protected void assertSuccessfulEmptyResponse(HttpResponse<String> response) {
        JsonObject responseBody = serializer.fromJson(response.body(), JsonObject.class);

        Assertions.assertEquals(200, response.statusCode());
        assertJsonContentType(response);
        Assertions.assertTrue(responseBody.isEmpty());
    }

    protected void assertErrorResponse(
            HttpResponse<String> response,
            int expectedStatus,
            String expectedMessage) {
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(expectedStatus, response.statusCode());
        assertJsonContentType(response);
        Assertions.assertEquals(expectedMessage, result.message());
    }

    protected void assertJsonContentType(HttpResponse<String> response) {
        String contentType = response.headers()
                .firstValue("Content-Type")
                .orElse("");
        Assertions.assertTrue(contentType.startsWith(JSON_CONTENT_TYPE));
    }
}
