package server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.LoginRequest;
import service.request.RegisterRequest;
import service.result.ErrorResult;
import service.result.LoginResult;
import service.result.RegisterResult;
import testutil.FailingAuthDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class LogoutEndpointTests {

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
    public void logoutReturnsEmptySuccessResponse() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(serverUri);

        HttpResponse<String> response = logout(serverUri, registeredUser.authToken());
        JsonObject responseBody = serializer.fromJson(response.body(), JsonObject.class);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(responseBody.isEmpty());
    }

    @Test
    public void logoutInvalidatesAuthToken() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(serverUri);

        HttpResponse<String> firstResponse = logout(serverUri, registeredUser.authToken());
        HttpResponse<String> secondResponse = logout(serverUri, registeredUser.authToken());
        ErrorResult secondResult = serializer.fromJson(secondResponse.body(), ErrorResult.class);

        Assertions.assertEquals(200, firstResponse.statusCode());
        Assertions.assertEquals(401, secondResponse.statusCode());
        Assertions.assertEquals("Error: unauthorized", secondResult.message());
    }

    @Test
    public void logoutRejectsMissingAuthToken() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = logout(serverUri, null);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void logoutRejectsUnknownAuthToken() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = logout(serverUri, "unknown-token");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void logoutOnlyRemovesRequestedSession() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerUser(serverUri);
        LoginResult loggedInUser = loginUser(serverUri);

        HttpResponse<String> firstResponse = logout(serverUri, registeredUser.authToken());
        HttpResponse<String> secondResponse = logout(serverUri, loggedInUser.authToken());

        Assertions.assertEquals(200, firstResponse.statusCode());
        Assertions.assertEquals(200, secondResponse.statusCode());
    }

    @Test
    public void logoutDataAccessFailureReturnsInternalServerError()
            throws IOException, InterruptedException {
        Server server = new Server(
                new MemoryUserDAO(),
                new FailingAuthDAO(),
                new MemoryGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = logout(serverUri, "valid-looking-token");
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: Authentication storage is unavailable", result.message());
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
                serializer.toJson(request));

        Assertions.assertEquals(200, response.statusCode());
        return serializer.fromJson(response.body(), RegisterResult.class);
    }

    private LoginResult loginUser(URI serverUri) throws IOException, InterruptedException {
        LoginRequest request = new LoginRequest("reader", "secret-password");
        HttpResponse<String> response = post(
                serverUri.resolve("session"),
                serializer.toJson(request));

        Assertions.assertEquals(200, response.statusCode());
        return serializer.fromJson(response.body(), LoginResult.class);
    }

    private HttpResponse<String> logout(URI serverUri, String authToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(serverUri.resolve("session"))
                .DELETE();

        if (authToken != null) {
            requestBuilder.header("authorization", authToken);
        }

        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(URI endpoint, String requestBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
