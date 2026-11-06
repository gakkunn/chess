package ui;

import client.ChessClient;
import client.ServerFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.Server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ReplTests {

    private static Server server;
    private static ServerFacade facade;

    @BeforeAll
    static void startServer() {
        server = new Server();
        int port = server.run(0);
        facade = new ServerFacade(port);
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void runProcessesCommandsUntilQuit() {
        String output = runRepl("help\nquit\n");

        Assertions.assertAll(
                () -> Assertions.assertTrue(output.contains("Welcome to 240 Chess")),
                () -> Assertions.assertTrue(output.contains("[SIGNED_OUT] >>>")),
                () -> Assertions.assertTrue(output.contains("register")),
                () -> Assertions.assertTrue(output.contains("Goodbye.")));
    }

    @Test
    void runStopsCleanlyAtEndOfInput() {
        String output = Assertions.assertDoesNotThrow(() -> runRepl(""));

        Assertions.assertAll(
                () -> Assertions.assertTrue(output.contains("Welcome to 240 Chess")),
                () -> Assertions.assertTrue(output.contains("[SIGNED_OUT] >>>")));
    }

    private String runRepl(String inputText) {
        ByteArrayInputStream input = new ByteArrayInputStream(
                inputText.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Scanner scanner = new Scanner(input, StandardCharsets.UTF_8);
             PrintStream printStream = new PrintStream(
                     output,
                     true,
                     StandardCharsets.UTF_8)) {
            ChessClient client = new ChessClient(facade);
            Repl repl = new Repl(client, scanner, printStream);
            repl.run();
        }

        return output.toString(StandardCharsets.UTF_8);
    }
}
