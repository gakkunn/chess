package server;

import com.google.gson.Gson;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.RegisterRequest;
import service.result.ErrorResult;
import service.result.RegisterResult;
import testutil.FailingUserDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class RegisterEndpointTests {

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
    public void registerReturnsUsernameAndAuthToken() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server());
        RegisterRequest request = new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com");

        HttpResponse<String> response = post(endpoint, serializer.toJson(request));
        RegisterResult result = serializer.fromJson(response.body(), RegisterResult.class);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("reader", result.username());
        Assertions.assertNotNull(result.authToken());
        Assertions.assertFalse(result.authToken().isBlank());
    }

    @Test
    public void registerRejectsDuplicateUsername() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server());
        String requestBody = serializer.toJson(new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com"));
        post(endpoint, requestBody);

        HttpResponse<String> response = post(endpoint, requestBody);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(403, response.statusCode());
        Assertions.assertEquals("Error: already taken", result.message());
    }

    @Test
    public void registerRejectsMissingRequiredField() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server());
        String requestBody = serializer.toJson(new RegisterRequest(
                "reader",
                null,
                "reader@example.com"));

        HttpResponse<String> response = post(endpoint, requestBody);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(400, response.statusCode());
        Assertions.assertEquals("Error: bad request", result.message());
    }

    @Test
    public void registerDataAccessFailureReturnsInternalServerError()
            throws IOException, InterruptedException {
        Server server = new Server(
                new FailingUserDAO(),
                new MemoryAuthDAO(),
                new MemoryGameDAO());
        URI endpoint = startServer(server);
        String requestBody = serializer.toJson(new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com"));

        HttpResponse<String> response = post(endpoint, requestBody);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: User storage is unavailable", result.message());
    }

    private URI startServer(Server server) {
        int port = server.run(0);
        startedServers.add(server);
        return URI.create("http://localhost:" + port + "/user");
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
