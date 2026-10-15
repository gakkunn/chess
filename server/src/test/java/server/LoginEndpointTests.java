package server;

import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.LoginRequest;
import service.result.ErrorResult;
import service.result.LoginResult;
import service.result.RegisterResult;
import testutil.FailingUserDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

public class LoginEndpointTests extends EndpointTestSupport {

    @Test
    public void loginReturnsUsernameAndNewAuthToken() throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        registerSuccessfully(serverUri, "reader", "reader@example.com");

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
        registerSuccessfully(serverUri, "reader", "reader@example.com");

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
        registerSuccessfully(serverUri, "reader", "reader@example.com");

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
        RegisterResult registerResult = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");
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

    private HttpResponse<String> login(URI serverUri, LoginRequest loginRequest)
            throws IOException, InterruptedException {
        return sendRequest(
                serverUri.resolve("session"),
                "POST",
                serializer.toJson(loginRequest),
                null);
    }
}
