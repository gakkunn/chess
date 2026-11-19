package server.websocket;

import chess.ChessGame;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import server.Server;
import websocket.WebSocketMessageCodec;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

abstract class WebSocketEndpointTestSupport {

    protected final WebSocketMessageCodec codec = new WebSocketMessageCodec();
    protected final List<SocketClient> clients = new ArrayList<>();
    protected HttpClient httpClient;
    protected AuthDAO authDAO;
    protected GameDAO gameDAO;
    protected Server server;
    protected URI serverUri;
    protected int gameID;

    @BeforeEach
    public void setUp() throws Exception {
        authDAO = createAuthDAO();
        gameDAO = createGameDAO();
        authDAO.createAuth(new AuthData("white-token", "white"));
        authDAO.createAuth(new AuthData("black-token", "black"));
        authDAO.createAuth(new AuthData("observer-token", "observer"));
        gameID = gameDAO.createGame(new GameData(
                0, "white", "black", "Test Game", new ChessGame()));
        httpClient = HttpClient.newHttpClient();
        startServer(gameDAO);
    }

    protected AuthDAO createAuthDAO() throws DataAccessException {
        return new MemoryAuthDAO();
    }

    protected GameDAO createGameDAO() {
        return new MemoryGameDAO();
    }

    @AfterEach
    public void tearDown() {
        for (SocketClient client : clients) {
            client.socket.abort();
        }
        server.stop();
        httpClient.close();
    }

    protected void startServer(GameDAO games) {
        server = createServer(games);
        serverUri = URI.create("http://localhost:" + server.run(0) + "/");
    }

    protected Server createServer(GameDAO games) {
        return new Server(new MemoryUserDAO(), authDAO, games);
    }

    protected SocketClient openSocket() throws Exception {
        SocketClient client = new SocketClient();
        client.socket = httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(3))
                .buildAsync(URI.create(serverUri.toString().replace("http:", "ws:") + "ws"), client)
                .get(3, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    protected LoadGameMessage connect(SocketClient client, String token, Integer requestedGameID)
            throws Exception {
        client.send(new UserGameCommand(UserGameCommand.CommandType.CONNECT, token, requestedGameID));
        return Assertions.assertInstanceOf(LoadGameMessage.class, client.receive());
    }

    protected void assertNotice(SocketClient client, String username, String role) throws Exception {
        NotificationMessage notice = Assertions.assertInstanceOf(NotificationMessage.class, client.receive());
        Assertions.assertTrue(notice.getMessage().contains(username), notice.getMessage());
        Assertions.assertTrue(notice.getMessage().contains(role), notice.getMessage());
    }

    protected ErrorMessage assertError(SocketClient client) throws Exception {
        ErrorMessage error = Assertions.assertInstanceOf(ErrorMessage.class, client.receive());
        Assertions.assertTrue(error.getErrorMessage().contains("Error"));
        return error;
    }

    protected final class SocketClient implements WebSocket.Listener {
        protected final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        protected final StringBuilder pendingText = new StringBuilder();
        protected final CompletableFuture<Integer> closed = new CompletableFuture<>();
        protected volatile Throwable failure;
        protected WebSocket socket;

        protected void send(UserGameCommand command) throws Exception {
            sendRaw(codec.serialize(command));
        }

        protected void sendRaw(String json) throws Exception {
            socket.sendText(json, true).get(3, TimeUnit.SECONDS);
        }

        protected ServerMessage receive() throws Exception {
            String json = messages.poll(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(json, "No WebSocket message received; failure: " + failure);
            return codec.deserializeMessage(json);
        }

        protected void assertNoMessage() throws Exception {
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
