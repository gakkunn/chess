package server;

import com.google.gson.JsonObject;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.result.ErrorResult;
import service.result.LoginResult;
import service.result.RegisterResult;
import testutil.FailingAuthDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;

public class LogoutEndpointTests extends EndpointTestSupport {

    @Test
    public void logoutReturnsEmptySuccessResponse() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");

        HttpResponse<String> response = logout(serverUri, registeredUser.authToken());
        JsonObject responseBody = serializer.fromJson(response.body(), JsonObject.class);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(responseBody.isEmpty());
    }

    @Test
    public void logoutInvalidatesAuthToken() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");

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
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");
        LoginResult loggedInUser = loginSuccessfully(serverUri, "reader");

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

    private HttpResponse<String> logout(URI serverUri, String authToken)
            throws IOException, InterruptedException {
        return sendRequest(
                serverUri.resolve("session"),
                "DELETE",
                null,
                authToken);
    }
}
