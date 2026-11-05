package client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.Server;

import java.io.IOException;
import java.net.ServerSocket;

public class ChessClientTests {

    private static Server server;
    private static ServerFacade facade;

    private ChessClient client;

    @BeforeAll
    static void startServer() {
        server = new Server();
        int port = server.run(0);
        facade = new ServerFacade(port);
    }

    @BeforeEach
    void setUp() throws ServerFacadeException {
        facade.clear();
        client = new ChessClient(facade);
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void newClientStartsSignedOut() {
        Assertions.assertEquals(ClientState.SIGNED_OUT, client.getState());
    }

    @Test
    void helpListsPreloginCommands() {
        String response = client.evaluate("help");

        Assertions.assertAll(
                () -> Assertions.assertTrue(response.contains("register")),
                () -> Assertions.assertTrue(response.contains("login")),
                () -> Assertions.assertTrue(response.contains("help")),
                () -> Assertions.assertTrue(response.contains("quit")));
    }

    @Test
    void commandsIgnoreCaseAndSurroundingWhitespace() {
        String expected = client.evaluate("help");
        String actual = client.evaluate("   HeLp   ");

        Assertions.assertEquals(expected, actual);
    }

    @Test
    void quitTransitionsClientToQuitState() {
        String response = client.evaluate("QuIt");

        Assertions.assertAll(
                () -> Assertions.assertEquals("Goodbye.", response),
                () -> Assertions.assertEquals(ClientState.QUIT, client.getState()));
    }

    @Test
    void commandAfterQuitReportsClosedClient() {
        client.evaluate("quit");

        String response = client.evaluate("help");

        Assertions.assertEquals("The chess client is closed.", response);
    }

    @Test
    void unknownCommandReturnsHelpfulMessage() {
        String response = client.evaluate("not-a-command");

        Assertions.assertEquals(
                "Unknown command. Type 'help' to see available commands.",
                response);
    }

    @Test
    void blankCommandReturnsHelpfulMessage() {
        String response = client.evaluate("   ");

        Assertions.assertEquals(
                "Please enter a command. Type 'help' to see available commands.",
                response);
    }

    @Test
    void quitRejectsUnexpectedArguments() {
        String response = client.evaluate("quit now");

        Assertions.assertAll(
                () -> Assertions.assertEquals("Usage: quit", response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        client.getState()));
    }

    @Test
    void registerCreatesSessionAndSignsIn() {
        String response = client.evaluate(
                "register new-client register-password client@example.com");

        String help = client.evaluate("help");
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Signed in as new-client.",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_IN,
                        client.getState()),
                () -> Assertions.assertTrue(help.contains("create")),
                () -> Assertions.assertFalse(response.contains("authToken")));
    }

    @Test
    void loginCreatesSessionAndSignsIn() throws ServerFacadeException {
        facade.register(
                "existing-client",
                "login-password",
                "existing@example.com");

        String response = client.evaluate(
                "login existing-client login-password");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Signed in as existing-client.",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_IN,
                        client.getState()));
    }

    @Test
    void registerRejectsIncorrectArgumentCount() {
        String response = client.evaluate("register username password");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Usage: register <username> <password> <email>",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        client.getState()));
    }

    @Test
    void loginRejectsIncorrectArgumentCount() {
        String response = client.evaluate("login username");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Usage: login <username> <password>",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        client.getState()));
    }

    @Test
    void registerReportsDuplicateUsernameWithoutSigningIn()
            throws ServerFacadeException {
        facade.register(
                "duplicate-client",
                "first-password",
                "first@example.com");

        String response = client.evaluate(
                "register duplicate-client second-password second@example.com");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "That username is already taken.",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        client.getState()));
    }

    @Test
    void loginReportsIncorrectCredentialsWithoutSigningIn()
            throws ServerFacadeException {
        facade.register(
                "password-client",
                "correct-password",
                "password-client@example.com");

        String response = client.evaluate(
                "login password-client incorrect-password");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "The username or password is incorrect.",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        client.getState()));
    }

    @Test
    void registerReportsUnavailableServerWithoutCrashing() throws IOException {
        int unavailablePort = findUnavailablePort();
        ChessClient unavailableClient = new ChessClient(
                new ServerFacade(unavailablePort));

        String response = unavailableClient.evaluate(
                "register offline password offline@example.com");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Unable to reach the chess server.",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        unavailableClient.getState()));
    }

    private int findUnavailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
