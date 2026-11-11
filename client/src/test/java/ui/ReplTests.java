package ui;

import client.ChessClient;
import client.GameSummary;
import client.ServerFacade;
import client.ServerFacadeException;
import model.AuthData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.Server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @BeforeEach
    void clearDatabase() throws ServerFacadeException {
        facade.clear();
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

    @Test
    void replCompletesThePregameWorkflow() throws ServerFacadeException {
        String input = String.join(
                System.lineSeparator(),
                "register workflow-player password workflow@example.com",
                "create End to End Match",
                "list",
                "play 1 WHITE",
                "logout",
                "quit",
                "");

        String output = runRepl(input);
        AuthData verifier = facade.login("workflow-player", "password");
        List<GameSummary> games = facade.listGames(verifier.authToken());

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        output.contains("Signed in as workflow-player.")),
                () -> Assertions.assertTrue(
                        output.contains("Created game 'End to End Match'.")),
                () -> Assertions.assertTrue(
                        output.contains("1. End to End Match")),
                () -> Assertions.assertTrue(
                        output.contains("Joined 'End to End Match' as WHITE.")),
                () -> Assertions.assertTrue(output.contains("Signed out.")),
                () -> Assertions.assertTrue(output.contains("Goodbye.")),
                () -> Assertions.assertTrue(
                        output.contains("[SIGNED_OUT] >>> ")),
                () -> Assertions.assertTrue(
                        output.contains("[SIGNED_IN] >>> ")),
                () -> Assertions.assertEquals(1, games.size()),
                () -> Assertions.assertEquals(
                        "workflow-player",
                        games.getFirst().whiteUsername()));
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
