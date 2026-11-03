package client;

import model.AuthData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.Server;

import java.io.IOException;
import java.net.ServerSocket;

public class ServerFacadeTests {

    private static Server server;
    private static ServerFacade facade;

    @BeforeAll
    public static void init() {
        server = new Server();
        var port = server.run(0);
        System.out.println("Started test HTTP server on " + port);
        facade = new ServerFacade(port);
    }

    @BeforeEach
    void clearDatabase() throws ServerFacadeException {
        facade.clear();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void clearSucceedsWhenServerIsAvailable() {
        Assertions.assertDoesNotThrow(facade::clear);
    }

    @Test
    void clearReportsWhenServerIsUnavailable() throws IOException {
        int unavailablePort = findUnavailablePort();
        ServerFacade unavailableFacade = new ServerFacade(unavailablePort);

        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                unavailableFacade::clear);

        Assertions.assertEquals(
                "Unable to reach the chess server.",
                exception.getMessage());
    }

    @Test
    void registerCreatesAnAuthenticatedUser() throws ServerFacadeException {
        String username = "new-player";

        AuthData authData = facade.register(
                username,
                "secure-password",
                "player@example.com");

        Assertions.assertAll(
                () -> Assertions.assertEquals(username, authData.username()),
                () -> Assertions.assertNotNull(authData.authToken()),
                () -> Assertions.assertTrue(authData.authToken().length() > 10));
    }

    @Test
    void registerRejectsDuplicateUsername() throws ServerFacadeException {
        String username = "duplicate-player";
        facade.register(username, "first-password", "first@example.com");

        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                () -> facade.register(
                        username,
                        "second-password",
                        "second@example.com"));

        Assertions.assertEquals("Error: already taken", exception.getMessage());
    }

    @Test
    void loginAuthenticatesRegisteredUser() throws ServerFacadeException {
        String username = "returning-player";
        String password = "correct-password";
        AuthData registration = facade.register(
                username,
                password,
                "returning@example.com");

        AuthData login = facade.login(username, password);

        Assertions.assertAll(
                () -> Assertions.assertEquals(username, login.username()),
                () -> Assertions.assertNotNull(login.authToken()),
                () -> Assertions.assertTrue(login.authToken().length() > 10),
                () -> Assertions.assertNotEquals(
                        registration.authToken(),
                        login.authToken()));
    }

    @Test
    void loginRejectsIncorrectPassword() throws ServerFacadeException {
        String username = "password-test-player";
        facade.register(
                username,
                "correct-password",
                "password-test@example.com");

        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                () -> facade.login(username, "incorrect-password"));

        Assertions.assertEquals("Error: unauthorized", exception.getMessage());
    }

    @Test
    void loginRejectsUnknownUser() {
        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                () -> facade.login("unknown-player", "any-password"));

        Assertions.assertEquals("Error: unauthorized", exception.getMessage());
    }

    private int findUnavailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
