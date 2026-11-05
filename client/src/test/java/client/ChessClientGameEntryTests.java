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
import java.util.regex.Pattern;

public class ChessClientGameEntryTests {

    private static final Pattern ANSI_SEQUENCE =
            Pattern.compile("\u001B\\[[;\\d]*m");

    private static Server server;
    private static ServerFacade facade;

    private ChessClient client;
    private AuthData verifier;

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
                "register signed-in-player password player@example.com");
        verifier = facade.login("signed-in-player", "password");
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void playWhiteJoinsSelectedListedGameAndDrawsWhitePerspective()
            throws ServerFacadeException {
        facade.createGame(verifier.authToken(), "First Match");
        int selectedGameID = facade.createGame(
                verifier.authToken(),
                "Selected Match");
        client.evaluate("list");

        String response = client.evaluate("play 2 white");
        GameSummary selectedGame = findGame(selectedGameID);
        String plainResponse = plainText(response);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "signed-in-player",
                        selectedGame.whiteUsername()),
                () -> Assertions.assertNull(selectedGame.blackUsername()),
                () -> Assertions.assertTrue(
                        plainResponse.startsWith(
                                "Joined 'Selected Match' as WHITE.")),
                () -> Assertions.assertTrue(
                        plainResponse.contains("a  b  c  d  e  f  g  h")),
                () -> Assertions.assertFalse(
                        response.contains("Game ID")));
    }

    @Test
    void playBlackJoinsGameAndDrawsBlackPerspective()
            throws ServerFacadeException {
        int gameID = facade.createGame(
                verifier.authToken(),
                "Black Perspective");
        client.evaluate("list");

        String response = client.evaluate("PLAY 1 BLACK");
        GameSummary game = findGame(gameID);
        String plainResponse = plainText(response);

        Assertions.assertAll(
                () -> Assertions.assertNull(game.whiteUsername()),
                () -> Assertions.assertEquals(
                        "signed-in-player",
                        game.blackUsername()),
                () -> Assertions.assertTrue(
                        plainResponse.startsWith(
                                "Joined 'Black Perspective' as BLACK.")),
                () -> Assertions.assertTrue(
                        plainResponse.contains("h  g  f  e  d  c  b  a")));
    }

    @Test
    void playRejectsIncorrectArgumentCount() {
        String response = client.evaluate("play 1");

        Assertions.assertEquals(
                "Usage: play <number> <WHITE|BLACK>",
                response);
    }

    @Test
    void playRejectsNonNumericGameNumber() throws ServerFacadeException {
        facade.createGame(verifier.authToken(), "Numbered Match");
        client.evaluate("list");

        String response = client.evaluate("play first WHITE");

        Assertions.assertEquals(invalidGameNumberMessage(), response);
    }

    @Test
    void playRejectsGameNumberOutsideLatestList()
            throws ServerFacadeException {
        facade.createGame(verifier.authToken(), "Only Match");
        client.evaluate("list");

        String response = client.evaluate("play 2 WHITE");

        Assertions.assertEquals(invalidGameNumberMessage(), response);
    }

    @Test
    void playRequiresARecentlyDisplayedList() throws ServerFacadeException {
        facade.createGame(verifier.authToken(), "Unlisted Match");

        String response = client.evaluate("play 1 WHITE");

        Assertions.assertEquals(invalidGameNumberMessage(), response);
    }

    @Test
    void playRejectsInvalidColor() throws ServerFacadeException {
        facade.createGame(verifier.authToken(), "Color Match");
        client.evaluate("list");

        String response = client.evaluate("play 1 GREEN");

        Assertions.assertEquals("Color must be WHITE or BLACK.", response);
    }

    @Test
    void playReportsOccupiedColorWithoutChangingClientState()
            throws ServerFacadeException {
        AuthData occupyingPlayer = facade.register(
                "occupying-player",
                "occupying-password",
                "occupying@example.com");
        int gameID = facade.createGame(
                occupyingPlayer.authToken(),
                "Occupied Match");
        facade.joinGame(
                occupyingPlayer.authToken(),
                ChessGame.TeamColor.WHITE,
                gameID);
        client.evaluate("list");

        String response = client.evaluate("play 1 WHITE");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "That color is already taken.",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_IN,
                        client.getState()));
    }

    @Test
    void playWithExpiredSessionReturnsClientToSignedOut()
            throws ServerFacadeException {
        facade.createGame(verifier.authToken(), "Expired Session Match");
        client.evaluate("list");
        facade.clear();

        String response = client.evaluate("play 1 WHITE");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Your session is no longer valid. Please sign in again.",
                        response),
                () -> Assertions.assertEquals(
                        ClientState.SIGNED_OUT,
                        client.getState()));
    }

    @Test
    void observeDrawsSelectedGameFromWhiteWithoutJoining()
            throws ServerFacadeException {
        facade.createGame(verifier.authToken(), "First Match");
        int observedGameID = facade.createGame(
                verifier.authToken(),
                "Observed Match");
        client.evaluate("list");

        String response = client.evaluate("observe 2");
        GameSummary observedGame = findGame(observedGameID);
        String plainResponse = plainText(response);

        Assertions.assertAll(
                () -> Assertions.assertNull(observedGame.whiteUsername()),
                () -> Assertions.assertNull(observedGame.blackUsername()),
                () -> Assertions.assertTrue(
                        plainResponse.startsWith("Observing 'Observed Match'.")),
                () -> Assertions.assertTrue(
                        plainResponse.contains("a  b  c  d  e  f  g  h")),
                () -> Assertions.assertFalse(
                        plainResponse.contains("Observing 'First Match'.")),
                () -> Assertions.assertFalse(
                        response.contains("Game ID")));
    }

    @Test
    void observeRejectsIncorrectArgumentCount() {
        String response = client.evaluate("observe");

        Assertions.assertEquals("Usage: observe <number>", response);
    }

    @Test
    void observeRejectsInvalidGameNumber() throws ServerFacadeException {
        facade.createGame(verifier.authToken(), "Observed Match");
        client.evaluate("list");

        String response = client.evaluate("observe zero");

        Assertions.assertEquals(invalidGameNumberMessage(), response);
    }

    private GameSummary findGame(int gameID) throws ServerFacadeException {
        List<GameSummary> games = facade.listGames(verifier.authToken());

        return games.stream()
                .filter(game -> game.gameID() == gameID)
                .findFirst()
                .orElseThrow();
    }

    private String plainText(String text) {
        return ANSI_SEQUENCE.matcher(text).replaceAll("");
    }

    private String invalidGameNumberMessage() {
        return "Choose a valid game number from the most recent list. "
                + "Type 'list' to refresh it.";
    }
}
