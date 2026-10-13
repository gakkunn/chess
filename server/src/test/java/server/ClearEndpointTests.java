package server;

import com.google.gson.Gson;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.result.ErrorResult;
import testutil.FailingAuthDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ClearEndpointTests {

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
    public void deleteDatabaseReturnsEmptySuccessResponse() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server());

        HttpResponse<String> response = delete(endpoint);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("{}", response.body());
        Assertions.assertTrue(response.headers()
                .firstValue("Content-Type")
                .orElse("")
                .startsWith("application/json"));
    }

    @Test
    public void deleteDatabaseCanRunMoreThanOnce() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server());

        HttpResponse<String> firstResponse = delete(endpoint);
        HttpResponse<String> secondResponse = delete(endpoint);

        Assertions.assertEquals(200, firstResponse.statusCode());
        Assertions.assertEquals(200, secondResponse.statusCode());
        Assertions.assertEquals("{}", secondResponse.body());
    }

    @Test
    public void dataAccessFailureReturnsJsonInternalServerError() throws IOException, InterruptedException {
        Server server = new Server(
                new MemoryUserDAO(),
                new FailingAuthDAO(),
                new MemoryGameDAO());
        URI endpoint = startServer(server);

        HttpResponse<String> response = delete(endpoint);
        ErrorResult errorResult = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertNotNull(errorResult);
        Assertions.assertTrue(errorResult.message().startsWith("Error:"));
        Assertions.assertTrue(errorResult.message().contains("Authentication storage is unavailable"));
    }

    private URI startServer(Server server) {
        int port = server.run(0);
        startedServers.add(server);
        return URI.create("http://localhost:" + port + "/db");
    }

    private HttpResponse<String> delete(URI endpoint) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .DELETE()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
