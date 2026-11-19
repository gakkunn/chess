package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import testutil.FailingGameDAO;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class WebSocketConnectTests extends WebSocketEndpointTestSupport {

    @Test
    public void playersAndObserversReceiveOnlyTheRequiredMessages() throws Exception {
        SocketClient white = openSocket();
        SocketClient black = openSocket();
        SocketClient observer = openSocket();
        GameData before = gameDAO.getGame(gameID);

        connect(white, "white-token", gameID);
        connect(black, "black-token", gameID);
        assertNotice(white, "black", "BLACK");
        connect(observer, "observer-token", gameID);
        assertNotice(white, "observer", "observer");
        assertNotice(black, "observer", "observer");

        white.assertNoMessage();
        black.assertNoMessage();
        observer.assertNoMessage();
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
    }

    @Test
    public void connectingLoadsTheStoredBoardAndCompletionState() throws Exception {
        GameData stored = gameDAO.getGame(gameID);
        stored.game().makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        stored.game().endGame();
        gameDAO.updateGame(stored);

        LoadGameMessage received = connect(openSocket(), "white-token", gameID);

        Assertions.assertEquals(stored.game(), received.getGame());
        Assertions.assertTrue(received.getGame().isGameOver());
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, received.getGame().getTeamTurn());
    }

    @Test
    public void notificationsIdentifyWhitePlayers() throws Exception {
        SocketClient observer = openSocket();
        connect(observer, "observer-token", gameID);

        connect(openSocket(), "white-token", gameID);

        assertNotice(observer, "white", "WHITE");
    }

    @ParameterizedTest
    @MethodSource("invalidConnections")
    public void invalidConnectDoesNotSubscribeOrNotifyOthers(String token, Integer requestedGameID)
            throws Exception {
        SocketClient white = openSocket();
        SocketClient invalid = openSocket();
        connect(white, "white-token", gameID);

        invalid.send(new UserGameCommand(UserGameCommand.CommandType.CONNECT, token, requestedGameID));

        assertError(invalid);
        white.assertNoMessage();
        connect(openSocket(), "observer-token", gameID);
        assertNotice(white, "observer", "observer");
        invalid.assertNoMessage();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "[]", "{}", "{",
            "{\"commandType\":\"UNKNOWN\"}", "{\"commandType\":\"MAKE_MOVE\"}"})
    public void malformedMessagesReturnAnErrorAndKeepTheSocketUsable(String json) throws Exception {
        SocketClient white = openSocket();
        SocketClient invalid = openSocket();
        connect(white, "white-token", gameID);

        invalid.sendRaw(json);

        assertError(invalid);
        white.assertNoMessage();
        connect(invalid, "observer-token", gameID);
        assertNotice(white, "observer", "observer");
        invalid.assertNoMessage();
    }

    @Test
    public void revokedAuthenticationIsRejectedWithoutNotifyingOthers() throws Exception {
        SocketClient observer = openSocket();
        connect(observer, "observer-token", gameID);
        authDAO.deleteAuth("white-token");
        SocketClient rejected = openSocket();

        rejected.send(new UserGameCommand(UserGameCommand.CommandType.CONNECT, "white-token", gameID));

        assertError(rejected);
        observer.assertNoMessage();
    }

    @Test
    public void repeatedConnectReloadsTheBoardWithoutDuplicateNotices() throws Exception {
        SocketClient white = openSocket();
        SocketClient observer = openSocket();
        connect(white, "white-token", gameID);
        connect(observer, "observer-token", gameID);
        assertNotice(white, "observer", "observer");

        connect(white, "white-token", gameID);

        white.assertNoMessage();
        observer.assertNoMessage();
    }

    @Test
    public void socketCannotSwitchItsGameOrIdentity() throws Exception {
        int otherGameID = gameDAO.createGame(new GameData(
                0, null, null, "Other Game", new ChessGame()));
        SocketClient white = openSocket();
        SocketClient observer = openSocket();
        SocketClient otherGame = openSocket();
        connect(white, "white-token", gameID);
        connect(observer, "observer-token", gameID);
        assertNotice(white, "observer", "observer");
        connect(otherGame, "black-token", otherGameID);

        white.send(new UserGameCommand(UserGameCommand.CommandType.CONNECT, "white-token", otherGameID));
        assertError(white);
        white.send(new UserGameCommand(UserGameCommand.CommandType.CONNECT, "black-token", gameID));
        assertError(white);

        observer.assertNoMessage();
        otherGame.assertNoMessage();
        connect(openSocket(), "black-token", gameID);
        assertNotice(white, "black", "BLACK");
        assertNotice(observer, "black", "BLACK");
        otherGame.assertNoMessage();
    }

    @Test
    public void connectionsForTheSameUsernameRemainIndependent() throws Exception {
        SocketClient first = openSocket();
        SocketClient second = openSocket();
        connect(first, "white-token", gameID);
        connect(second, "white-token", gameID);
        assertNotice(first, "white", "WHITE");

        connect(openSocket(), "observer-token", gameID);

        assertNotice(first, "observer", "observer");
        assertNotice(second, "observer", "observer");
    }

    @Test
    public void gamesDoNotReceiveEachOthersConnectNotices() throws Exception {
        int otherGameID = gameDAO.createGame(new GameData(
                0, "white", "black", "Other Game", new ChessGame()));
        SocketClient white = openSocket();
        SocketClient otherGame = openSocket();
        connect(white, "white-token", gameID);
        connect(otherGame, "black-token", otherGameID);

        connect(openSocket(), "observer-token", gameID);

        assertNotice(white, "observer", "observer");
        white.assertNoMessage();
        otherGame.assertNoMessage();
    }

    @Test
    public void closingASocketKeepsThePlayerSlotAndAllowsReconnection() throws Exception {
        SocketClient original = openSocket();
        connect(original, "white-token", gameID);

        original.socket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(3, TimeUnit.SECONDS);
        original.closed.get(3, TimeUnit.SECONDS);

        Assertions.assertEquals("white", gameDAO.getGame(gameID).whiteUsername());
        connect(openSocket(), "white-token", gameID);
    }

    @Test
    public void databaseClearClosesSubscriptionsBeforeGameIdentifiersAreReused() throws Exception {
        SocketClient oldClient = openSocket();
        connect(oldClient, "white-token", gameID);

        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(serverUri.resolve("db"))
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());

        Assertions.assertEquals(200, response.statusCode());
        oldClient.closed.get(3, TimeUnit.SECONDS);
        authDAO.createAuth(new AuthData("new-token", "new-player"));
        int newGameID = gameDAO.createGame(new GameData(
                0, "new-player", null, "New Game", new ChessGame()));
        Assertions.assertEquals(gameID, newGameID);
        connect(openSocket(), "new-token", newGameID);
        oldClient.assertNoMessage();
    }

    @Test
    public void storageFailureReturnsAnErrorWithoutInternalDetails() throws Exception {
        server.stop();
        startServer(new FailingGameDAO());
        SocketClient client = openSocket();

        client.send(new UserGameCommand(UserGameCommand.CommandType.CONNECT, "white-token", gameID));

        ErrorMessage error = assertError(client);
        Assertions.assertEquals("Error: internal server error", error.getErrorMessage());
    }

    private static Stream<Arguments> invalidConnections() {
        return Stream.of(
                Arguments.of("bad-token", 1),
                Arguments.of(null, 1),
                Arguments.of("   ", 1),
                Arguments.of("white-token", null),
                Arguments.of("white-token", 0),
                Arguments.of("white-token", -1),
                Arguments.of("white-token", 999));
    }

}
