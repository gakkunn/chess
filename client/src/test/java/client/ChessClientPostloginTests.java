package client;

import chess.ChessGame;
import model.AuthData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.Server;

import java.util.List;

public class ChessClientPostloginTests {

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
        client.evaluate(
                "register signed-in-client password client@example.com");
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void logoutEndsSessionAndReturnsToSignedOut() {
        String response = client.evaluate("logout");

        Assertions.assertAll(
                () -> Assertions.assertEquals("Signed out.", response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        client.getState()),
                () -> Assertions.assertEquals(
                        "Unknown command. Type 'help' to see available commands.",
                        client.evaluate("list")));
    }

    @Test
    void logoutRejectsUnexpectedArguments() {
        String response = client.evaluate("logout now");

        Assertions.assertAll(
                () -> Assertions.assertEquals("Usage: logout", response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_IN,
                        client.getState()));
    }

    @Test
    void helpListsEveryPostloginCommand() {
        String response = client.evaluate("help");

        Assertions.assertAll(
                () -> Assertions.assertTrue(response.contains("create")),
                () -> Assertions.assertTrue(response.contains("list")),
                () -> Assertions.assertTrue(response.contains("play")),
                () -> Assertions.assertTrue(response.contains("observe")),
                () -> Assertions.assertTrue(response.contains("logout")),
                () -> Assertions.assertTrue(response.contains("help")),
                () -> Assertions.assertTrue(response.contains("quit")));
    }

    @Test
    void createGameCreatesNamedGameWithoutJoining()
            throws ServerFacadeException {
        String response = client.evaluate("create Weekend Match");

        AuthData verifier = facade.login("signed-in-client", "password");
        List<GameSummary> games = facade.listGames(verifier.authToken());

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Created game 'Weekend Match'.",
                        response),
                () -> Assertions.assertEquals(1, games.size()),
                () -> Assertions.assertEquals(
                        "Weekend Match",
                        games.getFirst().gameName()),
                () -> Assertions.assertNull(
                        games.getFirst().whiteUsername()),
                () -> Assertions.assertNull(
                        games.getFirst().blackUsername()),
                () -> Assertions.assertFalse(response.contains("gameID")));
    }

    @Test
    void createGameRejectsMissingName() {
        String response = client.evaluate("create");

        Assertions.assertEquals("Usage: create <name>", response);
    }

    @Test
    void listGamesReportsWhenNoGamesExist() {
        String response = client.evaluate("list");

        Assertions.assertEquals("No games are available.", response);
    }

    @Test
    void listGamesDisplaysNumberedGamesWithoutInternalIdentifiers()
            throws ServerFacadeException {
        AuthData verifier = facade.login("signed-in-client", "password");
        facade.createGame(
                verifier.authToken(),
                "First Match");
        facade.createGame(
                verifier.authToken(),
                "Second Match");

        String response = client.evaluate("list");

        String expected = String.join(
                System.lineSeparator(),
                "Available games:",
                "  1. First Match | White: Open | Black: Open",
                "  2. Second Match | White: Open | Black: Open");

        Assertions.assertEquals(expected, response);
    }

    @Test
    void listGamesDisplaysCurrentPlayers() throws ServerFacadeException {
        AuthData whitePlayer = facade.register(
                "white-player",
                "white-password",
                "white@example.com");
        AuthData blackPlayer = facade.register(
                "black-player",
                "black-password",
                "black@example.com");
        int gameID = facade.createGame(
                whitePlayer.authToken(),
                "Occupied Match");
        facade.joinGame(
                whitePlayer.authToken(),
                ChessGame.TeamColor.WHITE,
                gameID);
        facade.joinGame(
                blackPlayer.authToken(),
                ChessGame.TeamColor.BLACK,
                gameID);

        String response = client.evaluate("list");

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        response.contains("White: white-player")),
                () -> Assertions.assertTrue(
                        response.contains("Black: black-player")));
    }

    @Test
    void listGamesRejectsUnexpectedArguments() {
        String response = client.evaluate("list games");

        Assertions.assertEquals("Usage: list", response);
    }

    @Test
    void expiredSessionReturnsClientToSignedOut()
            throws ServerFacadeException {
        facade.clear();

        String response = client.evaluate("list");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Your session is no longer valid. Please sign in again.",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        client.getState()));
    }
}
