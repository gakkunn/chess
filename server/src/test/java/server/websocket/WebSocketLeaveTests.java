package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import dataaccess.MemoryGameDAO;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import websocket.commands.MakeMoveCommand;
import websocket.messages.LoadGameMessage;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public class WebSocketLeaveTests extends WebSocketEndpointTestSupport {

    private ControlledGameDAO controlledGames;
    private SocketClient white;
    private SocketClient black;
    private SocketClient observer;

    @Override
    protected MemoryGameDAO createGameDAO() {
        controlledGames = new ControlledGameDAO();
        return controlledGames;
    }

    @BeforeEach
    public void connectParticipants() throws Exception {
        white = openSocket();
        black = openSocket();
        observer = openSocket();
        connect(white, "white-token", gameID);
        connect(black, "black-token", gameID);
        assertNotice(white, "black", "BLACK");
        connect(observer, "observer-token", gameID);
        assertNotice(white, "observer", "observer");
        assertNotice(black, "observer", "observer");
    }

    @ParameterizedTest
    @EnumSource(ChessGame.TeamColor.class)
    public void playersCanLeaveAndReplacementPlayersContinueTheStoredGame(ChessGame.TeamColor color)
            throws Exception {
        ChessGame position = new ChessGame();
        position.makeMove(move(2, 5, 4, 5));
        if (color == ChessGame.TeamColor.WHITE) {
            position.makeMove(move(7, 5, 5, 5));
        }
        gameDAO.updateGame(new GameData(gameID, "white", "black", "Test Game", position));
        GameData before = snapshot();
        SocketClient departing = player(color);

        leave(departing, username(color) + "-token", gameID);

        assertLeaveDelivered(departing, username(color));
        GameData after = gameDAO.getGame(gameID);
        Assertions.assertNull(playerName(after, color));
        Assertions.assertEquals(before.game(), after.game());
        Assertions.assertEquals(before.gameName(), after.gameName());
        Assertions.assertEquals(color == ChessGame.TeamColor.WHITE ? "black" : "white",
                playerName(after, opposite(color)));
        Assertions.assertNotNull(authDAO.getAuth(username(color) + "-token"));

        authDAO.createAuth(new AuthData("replacement-token", "replacement"));
        joinGame("replacement-token", color, gameID);
        SocketClient replacement = openSocket();
        Assertions.assertEquals(before.game(), connect(replacement, "replacement-token", gameID).getGame());
        for (SocketClient client : List.of(white, black, observer)) {
            if (client != departing) {
                assertNotice(client, "replacement", color.name());
            }
        }
        departing.assertNoMessage();
        ChessMove knightMove = color == ChessGame.TeamColor.WHITE ? move(1, 7, 3, 6) : move(8, 7, 6, 6);
        ChessGame expected = new ChessGame(before.game());
        expected.makeMove(knightMove);
        replacement.send(new MakeMoveCommand("replacement-token", gameID, knightMove));
        assertReplacementMove(departing, replacement, expected);

        departing.send(new MakeMoveCommand(username(color) + "-token", gameID, knightMove));
        Assertions.assertEquals("Error: bad request", assertError(departing).getErrorMessage());
        Assertions.assertEquals(expected, gameDAO.getGame(gameID).game());
        assertAllQuiet();
        replacement.assertNoMessage();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void observersLeaveWithoutWritingOrChangingTheGame(boolean completed) throws Exception {
        if (completed) {
            gameDAO.getGame(gameID).game().endGame();
        }
        GameData before = snapshot();
        controlledGames.failUpdates = true;

        leave(observer, "observer-token", gameID);

        assertLeaveDelivered(observer, "observer");
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        SocketClient newObserver = openSocket();
        Assertions.assertEquals(before.game(), connect(newObserver, "observer-token", gameID).getGame());
        assertNotice(white, "observer", "observer");
        assertNotice(black, "observer", "observer");
        observer.assertNoMessage();
    }

    @Test
    public void playersCanLeaveCompletedGamesWithoutReopeningThem() throws Exception {
        gameDAO.getGame(gameID).game().endGame();
        GameData before = snapshot();

        leave(white, "white-token", gameID);

        assertLeaveDelivered(white, "white");
        GameData after = gameDAO.getGame(gameID);
        Assertions.assertNull(after.whiteUsername());
        Assertions.assertEquals("black", after.blackUsername());
        Assertions.assertEquals(before.game(), after.game());
        Assertions.assertTrue(after.game().isGameOver());
    }

    @ParameterizedTest
    @MethodSource("invalidLeaveRequests")
    public void invalidRequestsDoNotChangeMembershipOrNotifyOthers(String token, Integer requestedGameID)
            throws Exception {
        GameData before = snapshot();

        leave(white, token, requestedGameID);

        assertError(white);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
        leave(white, "white-token", gameID);
        assertLeaveDelivered(white, "white");
        Assertions.assertNull(gameDAO.getGame(gameID).whiteUsername());
    }

    @Test
    public void unconnectedSocketsCannotRemoveAPlayerWithAValidToken() throws Exception {
        SocketClient unconnected = openSocket();
        GameData before = snapshot();

        leave(unconnected, "white-token", gameID);

        assertError(unconnected);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    @Test
    public void revokedAuthenticationCannotChangeMembership() throws Exception {
        GameData before = snapshot();
        authDAO.deleteAuth("white-token");

        leave(white, "white-token", gameID);

        Assertions.assertEquals("Error: unauthorized", assertError(white).getErrorMessage());
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    @Test
    public void leaveDoesNotAffectAnotherGameOrItsObservers() throws Exception {
        int otherID = gameDAO.createGame(new GameData(
                0, "white", "black", "Other Game", new ChessGame()));
        SocketClient otherObserver = openSocket();
        connect(otherObserver, "observer-token", otherID);
        GameData before = snapshot();

        leave(white, "white-token", otherID);
        assertError(white);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
        otherObserver.assertNoMessage();

        leave(white, "white-token", gameID);
        assertLeaveDelivered(white, "white");
        otherObserver.assertNoMessage();
        Assertions.assertEquals("white", gameDAO.getGame(otherID).whiteUsername());
    }

    @Test
    public void repeatedLeaveReturnsAnErrorWithoutAnotherNotice() throws Exception {
        leave(white, "white-token", gameID);
        assertLeaveDelivered(white, "white");
        GameData before = snapshot();

        leave(white, "white-token", gameID);

        assertError(white);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    @Test
    public void leavingTheLastConnectionAllowsThatSocketToConnectElsewhere() throws Exception {
        leave(black, "black-token", gameID);
        assertLeaveDelivered(black, "black");
        leave(observer, "observer-token", gameID);
        assertNotice(white, "observer", "left the game.");
        observer.assertNoMessage();
        black.assertNoMessage();
        int otherID = gameDAO.createGame(new GameData(
                0, null, null, "Other Game", new ChessGame()));

        leave(white, "white-token", gameID);
        connect(white, "white-token", otherID);

        Assertions.assertNull(gameDAO.getGame(gameID).whiteUsername());
        Assertions.assertNull(gameDAO.getGame(gameID).blackUsername());
        Assertions.assertFalse(gameDAO.getGame(gameID).game().isGameOver());
        assertAllQuiet();
    }

    @Test
    public void staleConnectionsCannotClearAReplacementPlayersSlot() throws Exception {
        SocketClient secondWhite = openSocket();
        connect(secondWhite, "white-token", gameID);
        for (SocketClient client : List.of(white, black, observer)) {
            assertNotice(client, "white", "WHITE");
        }
        leave(white, "white-token", gameID);
        assertLeaveDelivered(white, "white");
        assertNotice(secondWhite, "white", "left the game.");
        authDAO.createAuth(new AuthData("replacement-token", "replacement"));
        joinGame("replacement-token", ChessGame.TeamColor.WHITE, gameID);
        SocketClient replacement = openSocket();
        connect(replacement, "replacement-token", gameID);
        for (SocketClient client : List.of(secondWhite, black, observer)) {
            assertNotice(client, "replacement", "WHITE");
        }

        leave(secondWhite, "white-token", gameID);

        for (SocketClient client : List.of(black, observer, replacement)) {
            assertNotice(client, "white", "left the game.");
        }
        Assertions.assertEquals("replacement", gameDAO.getGame(gameID).whiteUsername());
        secondWhite.assertNoMessage();
        white.assertNoMessage();
    }

    @Test
    public void leavingRemovesBothColorsWhenTheUserOwnsBoth() throws Exception {
        gameDAO.updateGame(new GameData(gameID, "white", "white", "Shared Colors", new ChessGame()));
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());

        leave(white, "white-token", gameID);

        assertLeaveDelivered(white, "white");
        GameData stored = gameDAO.getGame(gameID);
        Assertions.assertNull(stored.whiteUsername());
        Assertions.assertNull(stored.blackUsername());
        Assertions.assertEquals(before, stored.game());
    }

    @Test
    public void failedSavesKeepThePlayerConnectedAndAllowRetrying() throws Exception {
        GameData before = snapshot();
        controlledGames.failUpdates = true;

        leave(white, "white-token", gameID);

        assertError(white);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
        SocketClient extraObserver = openSocket();
        connect(extraObserver, "observer-token", gameID);
        for (SocketClient client : List.of(white, black, observer)) {
            assertNotice(client, "observer", "observer");
        }
        controlledGames.failUpdates = false;
        leave(white, "white-token", gameID);
        assertLeaveDelivered(white, "white");
        assertNotice(extraObserver, "white", "left the game.");
        Assertions.assertNull(gameDAO.getGame(gameID).whiteUsername());
    }

    @Test
    public void aPendingLeaveSaveBlocksReplacementJoinsAndPreventsEarlyNotices() throws Exception {
        authDAO.createAuth(new AuthData("replacement-token", "replacement"));
        GameData before = snapshot();
        controlledGames.blockNextUpdate.set(true);
        CompletableFuture<HttpResponse<String>> joining;

        try {
            leave(white, "white-token", gameID);
            Assertions.assertTrue(controlledGames.saveStarted.await(3, TimeUnit.SECONDS));
            Assertions.assertEquals(before, gameDAO.getGame(gameID));
            joining = httpClient.sendAsync(joinRequest("replacement-token", ChessGame.TeamColor.WHITE, gameID),
                    HttpResponse.BodyHandlers.ofString());
            Assertions.assertThrows(TimeoutException.class, () -> joining.get(100, TimeUnit.MILLISECONDS));
            assertAllQuiet();
        } finally {
            controlledGames.allowSave.countDown();
        }

        HttpResponse<String> joined = joining.get(3, TimeUnit.SECONDS);
        Assertions.assertEquals(200, joined.statusCode(), joined.body());
        assertLeaveDelivered(white, "white");
        Assertions.assertEquals("replacement", gameDAO.getGame(gameID).whiteUsername());
        Assertions.assertEquals(before.game(), gameDAO.getGame(gameID).game());
    }

    private static Stream<Arguments> invalidLeaveRequests() {
        return Stream.of(
                Arguments.of("bad-token", 1),
                Arguments.of(null, 1),
                Arguments.of("", 1),
                Arguments.of("black-token", 1),
                Arguments.of("white-token", null),
                Arguments.of("white-token", 0),
                Arguments.of("white-token", -1),
                Arguments.of("white-token", 999));
    }

    private GameData snapshot() throws Exception {
        GameData stored = gameDAO.getGame(gameID);
        return new GameData(stored.gameID(), stored.whiteUsername(), stored.blackUsername(),
                stored.gameName(), new ChessGame(stored.game()));
    }

    private SocketClient player(ChessGame.TeamColor color) {
        return color == ChessGame.TeamColor.WHITE ? white : black;
    }

    private String username(ChessGame.TeamColor color) {
        return color == ChessGame.TeamColor.WHITE ? "white" : "black";
    }

    private ChessGame.TeamColor opposite(ChessGame.TeamColor color) {
        return color == ChessGame.TeamColor.WHITE ? ChessGame.TeamColor.BLACK : ChessGame.TeamColor.WHITE;
    }

    private String playerName(GameData game, ChessGame.TeamColor color) {
        return color == ChessGame.TeamColor.WHITE ? game.whiteUsername() : game.blackUsername();
    }

    private ChessMove move(int fromRow, int fromColumn, int toRow, int toColumn) {
        return new ChessMove(new ChessPosition(fromRow, fromColumn), new ChessPosition(toRow, toColumn), null);
    }

    private void assertLeaveDelivered(SocketClient departing, String username) throws Exception {
        for (SocketClient client : List.of(white, black, observer)) {
            if (client != departing) {
                assertNotice(client, username, "left the game.");
            }
        }
        assertAllQuiet();
    }

    private void assertReplacementMove(SocketClient departed, SocketClient replacement, ChessGame expected)
            throws Exception {
        for (SocketClient client : List.of(white, black, observer, replacement)) {
            if (client == departed) {
                client.assertNoMessage();
            } else {
                Assertions.assertEquals(expected,
                        Assertions.assertInstanceOf(LoadGameMessage.class, client.receive()).getGame());
                if (client != replacement) {
                    assertNotice(client, "replacement", "moved");
                }
            }
        }
        Assertions.assertEquals(expected, gameDAO.getGame(gameID).game());
    }

    private void assertAllQuiet() throws Exception {
        white.assertNoMessage();
        black.assertNoMessage();
        observer.assertNoMessage();
    }
}
