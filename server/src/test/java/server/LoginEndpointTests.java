package server;

import com.google.gson.Gson;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.LoginRequest;
import service.request.RegisterRequest;
import service.result.ErrorResult;
import service.result.LoginResult;
import service.result.RegisterResult;
import testutil.FailingUserDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LoginEndpointTests {

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
    public void loginReturnsUsernameAndNewAuthToken() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        registerUser(serverUri);

        HttpResponse<String> response = login(
                serverUri,
                new LoginRequest("reader", "secret-password"));
        LoginResult result = serializer.fromJson(response.body(), LoginResult.class);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("reader", result.username());
        Assertions.assertNotNull(result.authToken());
        Assertions.assertFalse(result.authToken().isBlank());
    }

    @Test
    public void loginRejectsMissingRequiredField() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        registerUser(serverUri);

        HttpResponse<String> response = login(
                serverUri,
                new LoginRequest("reader", null));
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(400, response.statusCode());
        Assertions.assertEquals("Error: bad request", result.message());
    }

    @Test
    public void loginRejectsUnknownUsername() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = login(
                serverUri,
                new LoginRequest("missing-user", "secret-password"));
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void loginRejectsIncorrectPassword() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        registerUser(serverUri);

        HttpResponse<String> response = login(
                serverUri,
                new LoginRequest("reader", "incorrect-password"));
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(401, response.statusCode());
        Assertions.assertEquals("Error: unauthorized", result.message());
    }

    @Test
    public void repeatedLoginsReturnUniqueAuthTokens() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registerResult = registerUser(serverUri);
        LoginRequest request = new LoginRequest("reader", "secret-password");

        LoginResult firstLogin = serializer.fromJson(
                login(serverUri, request).body(),
                LoginResult.class);
        LoginResult secondLogin = serializer.fromJson(
                login(serverUri, request).body(),
                LoginResult.class);

        Set<String> authTokens = new HashSet<>();
        authTokens.add(registerResult.authToken());
        authTokens.add(firstLogin.authToken());
        authTokens.add(secondLogin.authToken());

        Assertions.assertEquals(3, authTokens.size());
    }

    @Test
    public void loginDataAccessFailureReturnsInternalServerError()
            throws IOException, InterruptedException {
        Server server = new Server(
                new FailingUserDAO(),
                new MemoryAuthDAO(),
                new MemoryGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = login(
                serverUri,
                new LoginRequest("reader", "secret-password"));
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: User storage is unavailable", result.message());
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

    private HttpResponse<String> login(URI serverUri, LoginRequest loginRequest)
            throws IOException, InterruptedException {
        return post(serverUri.resolve("session"), serializer.toJson(loginRequest));
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
