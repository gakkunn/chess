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
import java.util.List;

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

    @Test
    void logoutEndsAuthenticatedSession() throws ServerFacadeException {
        AuthData authData = facade.register(
                "logout-player",
                "logout-password",
                "logout@example.com");

        Assertions.assertDoesNotThrow(
                () -> facade.logout(authData.authToken()));
    }

    @Test
    void logoutRejectsInvalidAuthToken() {
        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                () -> facade.logout("invalid-auth-token"));

        Assertions.assertEquals("Error: unauthorized", exception.getMessage());
    }

    @Test
    void logoutPreventsAuthTokenReuse() throws ServerFacadeException {
        AuthData authData = facade.register(
                "single-session-player",
                "single-session-password",
                "single-session@example.com");
        facade.logout(authData.authToken());

        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                () -> facade.logout(authData.authToken()));

        Assertions.assertEquals("Error: unauthorized", exception.getMessage());
    }

    @Test
    void createGameReturnsPositiveGameID() throws ServerFacadeException {
        AuthData authData = facade.register(
                "game-creator",
                "creator-password",
                "creator@example.com");

        int gameID = facade.createGame(
                authData.authToken(),
                "Friendly Match");

        Assertions.assertTrue(gameID > 0);
    }

    @Test
    void createGameRejectsInvalidAuthToken() {
        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                () -> facade.createGame(
                        "invalid-auth-token",
                        "Unauthorized Match"));

        Assertions.assertEquals("Error: unauthorized", exception.getMessage());
    }

    @Test
    void createGameRejectsBlankName() throws ServerFacadeException {
        AuthData authData = facade.register(
                "blank-name-creator",
                "creator-password",
                "blank-name@example.com");

        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                () -> facade.createGame(authData.authToken(), "   "));

        Assertions.assertEquals("Error: bad request", exception.getMessage());
    }

    @Test
    void listGamesReturnsEmptyListInitially() throws ServerFacadeException {
        AuthData authData = facade.register(
                "empty-list-player",
                "list-password",
                "empty-list@example.com");

        List<GameSummary> games = facade.listGames(authData.authToken());

        Assertions.assertTrue(games.isEmpty());
    }

    @Test
    void listGamesReturnsCreatedGamesInOrder() throws ServerFacadeException {
        AuthData authData = facade.register(
                "game-list-player",
                "list-password",
                "game-list@example.com");
        int firstGameID = facade.createGame(
                authData.authToken(),
                "First Game");
        int secondGameID = facade.createGame(
                authData.authToken(),
                "Second Game");

        List<GameSummary> games = facade.listGames(authData.authToken());

        List<GameSummary> expectedGames = List.of(
                new GameSummary(firstGameID, null, null, "First Game"),
                new GameSummary(secondGameID, null, null, "Second Game"));
        Assertions.assertEquals(expectedGames, games);
    }

    @Test
    void listGamesRejectsInvalidAuthToken() {
        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                () -> facade.listGames("invalid-auth-token"));

        Assertions.assertEquals("Error: unauthorized", exception.getMessage());
    }

    private int findUnavailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
