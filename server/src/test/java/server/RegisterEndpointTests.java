package server;

import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.RegisterRequest;
import service.result.ErrorResult;
import service.result.RegisterResult;
import testutil.FailingUserDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;

public class RegisterEndpointTests extends EndpointTestSupport {

    @Test
    public void registerReturnsUsernameAndAuthToken() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server()).resolve("user");
        RegisterRequest request = new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com");

        HttpResponse<String> response = sendRequest(
                endpoint,
                "POST",
                serializer.toJson(request),
                null);
        RegisterResult result = serializer.fromJson(response.body(), RegisterResult.class);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("reader", result.username());
        Assertions.assertNotNull(result.authToken());
        Assertions.assertFalse(result.authToken().isBlank());
    }

    @Test
    public void registerRejectsDuplicateUsername() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server()).resolve("user");
        String requestBody = serializer.toJson(new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com"));
        sendRequest(endpoint, "POST", requestBody, null);

        HttpResponse<String> response = sendRequest(endpoint, "POST", requestBody, null);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(403, response.statusCode());
        Assertions.assertEquals("Error: already taken", result.message());
    }

    @Test
    public void registerRejectsMissingRequiredField() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server()).resolve("user");
        String requestBody = serializer.toJson(new RegisterRequest(
                "reader",
                null,
                "reader@example.com"));

        HttpResponse<String> response = sendRequest(endpoint, "POST", requestBody, null);
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
        URI endpoint = startServer(server).resolve("user");
        String requestBody = serializer.toJson(new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com"));

        HttpResponse<String> response = sendRequest(endpoint, "POST", requestBody, null);
        ErrorResult result = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertEquals("Error: User storage is unavailable", result.message());
    }

}
