package client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChessClientTests {

    private ChessClient client;

    @BeforeEach
    void setUp() {
        client = new ChessClient("http://localhost:8080");
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
}
