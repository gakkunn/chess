package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import server.Server;
import testutil.FailingGameDAO;
import websocket.WebSocketMessageCodec;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class WebSocketConnectTests {

    private final WebSocketMessageCodec codec = new WebSocketMessageCodec();
    private final List<SocketClient> clients = new ArrayList<>();
    private HttpClient httpClient;
    private MemoryAuthDAO authDAO;
    private MemoryGameDAO gameDAO;
    private Server server;
    private URI serverUri;
    private int gameID;

    @BeforeEach
    public void setUp() throws Exception {
        authDAO = new MemoryAuthDAO();
        gameDAO = new MemoryGameDAO();
        authDAO.createAuth(new AuthData("white-token", "white"));
        authDAO.createAuth(new AuthData("black-token", "black"));
        authDAO.createAuth(new AuthData("observer-token", "observer"));
        gameID = gameDAO.createGame(new GameData(
                0, "white", "black", "Test Game", new ChessGame()));
        httpClient = HttpClient.newHttpClient();
        startServer(gameDAO);
    }

    @AfterEach
    public void tearDown() {
        for (SocketClient client : clients) {
            client.socket.abort();
        }
        server.stop();
        httpClient.close();
    }

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

    private void startServer(GameDAO games) {
        server = new Server(new MemoryUserDAO(), authDAO, games);
        serverUri = URI.create("http://localhost:" + server.run(0) + "/");
    }

    private SocketClient openSocket() throws Exception {
        SocketClient client = new SocketClient();
        client.socket = httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(3))
                .buildAsync(URI.create(serverUri.toString().replace("http:", "ws:") + "ws"), client)
                .get(3, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    private LoadGameMessage connect(SocketClient client, String token, Integer requestedGameID)
            throws Exception {
        client.send(new UserGameCommand(UserGameCommand.CommandType.CONNECT, token, requestedGameID));
        return Assertions.assertInstanceOf(LoadGameMessage.class, client.receive());
    }

    private void assertNotice(SocketClient client, String username, String role) throws Exception {
        NotificationMessage notice = Assertions.assertInstanceOf(NotificationMessage.class, client.receive());
        Assertions.assertTrue(notice.getMessage().contains(username), notice.getMessage());
        Assertions.assertTrue(notice.getMessage().contains(role), notice.getMessage());
    }

    private ErrorMessage assertError(SocketClient client) throws Exception {
        ErrorMessage error = Assertions.assertInstanceOf(ErrorMessage.class, client.receive());
        Assertions.assertTrue(error.getErrorMessage().contains("Error"));
        return error;
    }

    private final class SocketClient implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder pendingText = new StringBuilder();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private volatile Throwable failure;
        private WebSocket socket;

        private void send(UserGameCommand command) throws Exception {
            sendRaw(codec.serialize(command));
        }

        private void sendRaw(String json) throws Exception {
            socket.sendText(json, true).get(3, TimeUnit.SECONDS);
        }

        private ServerMessage receive() throws Exception {
            String json = messages.poll(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(json, "No WebSocket message received; failure: " + failure);
            return codec.deserializeMessage(json);
        }

        private void assertNoMessage() throws Exception {
            Assertions.assertNull(messages.poll(150, TimeUnit.MILLISECONDS), "Unexpected WebSocket message.");
            Assertions.assertNull(failure, "WebSocket listener failed.");
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            pendingText.append(data);
            if (last) {
                messages.add(pendingText.toString());
                pendingText.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            failure = error;
            closed.completeExceptionally(error);
        }
    }
}
