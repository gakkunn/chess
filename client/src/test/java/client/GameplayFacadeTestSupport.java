package client;

import chess.ChessGame;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import model.AuthData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import server.Server;
import ui.BoardRenderer;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Starts a live server with a game joined by white and black, and creates clients connected to it.
 */
abstract class GameplayFacadeTestSupport {

    protected final List<ServerFacade> facades = new ArrayList<>();
    protected final List<Server> servers = new ArrayList<>();
    protected MemoryUserDAO users;
    protected MemoryAuthDAO auth;
    protected MemoryGameDAO games;
    protected Server server;
    protected int port;
    protected ServerFacade white;
    protected ServerFacade black;
    protected ServerFacade observer;
    protected AuthData whiteAuth;
    protected AuthData blackAuth;
    protected AuthData observerAuth;
    protected int gameID;
    protected final RecordingObserver whiteMessages = new RecordingObserver();
    protected final RecordingObserver blackMessages = new RecordingObserver();
    protected final RecordingObserver observerMessages = new RecordingObserver();

    @BeforeEach
    public void startServer() throws Exception {
        users = new MemoryUserDAO();
        auth = new MemoryAuthDAO();
        games = createGameDAO();
        startServer(0);
        white = facade(port);
        black = facade(port);
        observer = facade(port);
        whiteAuth = white.register("white", "password", "white@example.com");
        blackAuth = black.register("black", "password", "black@example.com");
        observerAuth = observer.register("observer", "password", "observer@example.com");
        gameID = white.createGame(whiteAuth.authToken(), "Live Game");
        white.joinGame(whiteAuth.authToken(), ChessGame.TeamColor.WHITE, gameID);
        black.joinGame(blackAuth.authToken(), ChessGame.TeamColor.BLACK, gameID);
    }

    @AfterEach
    public void releaseConnections() {
        for (ServerFacade facade : facades) {
            facade.close();
        }
        for (Server running : servers) {
            running.stop();
        }
    }

    protected MemoryGameDAO createGameDAO() {
        return new MemoryGameDAO();
    }

    protected void startServer(int requestedPort) {
        server = new Server(users, auth, games);
        port = server.run(requestedPort);
        servers.add(server);
    }

    protected void stopServer() {
        server.stop();
        servers.remove(server);
    }

    protected ServerFacade facade(int serverPort) {
        ServerFacade facade = new ServerFacade(serverPort);
        facades.add(facade);
        return facade;
    }

    /**
     * Signs in a registered user ("white", "black", or "observer") on a new server connection.
     */
    protected TestGameplayClient signIn(String username) throws Exception {
        ChessGame.TeamColor color = switch (username) {
            case "white" -> ChessGame.TeamColor.WHITE;
            case "black" -> ChessGame.TeamColor.BLACK;
            default -> null;
        };
        TestGameplayClient client = new TestGameplayClient(facade(port), color);
        Assertions.assertEquals("Signed in as " + username + ".", client.evaluate("login " + username + " password"));
        return client;
    }

    protected TestGameplayClient enter(String username) throws Exception {
        return enter(username, 1);
    }

    protected TestGameplayClient enter(String username, int gameNumber) throws Exception {
        TestGameplayClient client = signIn(username);
        client.enter(gameNumber);
        return client;
    }

    protected void connectParticipants() throws Exception {
        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);
        whiteMessages.take(LoadGameMessage.class);
        black.connectGame(blackAuth.authToken(), gameID, blackMessages);
        blackMessages.take(LoadGameMessage.class);
        whiteMessages.take(NotificationMessage.class);
        observer.connectGame(observerAuth.authToken(), gameID, observerMessages);
        observerMessages.take(LoadGameMessage.class);
        whiteMessages.take(NotificationMessage.class);
        blackMessages.take(NotificationMessage.class);
    }

    protected static class RecordingObserver implements ServerMessageObserver {
        protected final BlockingQueue<ServerMessage> messages = new LinkedBlockingQueue<>();
        protected final CountDownLatch disconnected = new CountDownLatch(1);
        protected final AtomicInteger disconnectCount = new AtomicInteger();

        @Override
        public void notify(ServerMessage message) {
            messages.add(message);
        }

        @Override
        public void onDisconnect() {
            disconnectCount.incrementAndGet();
            disconnected.countDown();
        }

        protected <T extends ServerMessage> T take(Class<T> type) throws Exception {
            ServerMessage message = messages.poll(3, TimeUnit.SECONDS);
            Assertions.assertNotNull(message, "No chess server message received.");
            return Assertions.assertInstanceOf(type, message);
        }

        protected void assertQuiet() throws Exception {
            Assertions.assertNull(messages.poll(150, TimeUnit.MILLISECONDS), "Unexpected chess server message.");
        }
    }

    /**
     * A chess client that records its displays so tests can wait for asynchronous server updates.
     */
    protected static final class TestGameplayClient implements AutoCloseable {

        final ChessClient client;
        final ServerFacade facade;
        final ChessGame.TeamColor color;
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        TestGameplayClient(ServerFacade facade, ChessGame.TeamColor color) {
            this.facade = facade;
            this.color = color;
            client = new ChessClient(facade);
            client.setNotificationListener(messages::add);
        }

        String evaluate(String command) {
            return client.evaluate(command);
        }

        void enter(int gameNumber) throws Exception {
            enter(color == null ? "observe " + gameNumber : "play " + gameNumber + " " + color);
        }

        void enter(String command) throws Exception {
            evaluate("list");
            evaluate(command);
            await(text -> text.startsWith("Joined") || text.startsWith("Observing"));
            Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        }

        String await(Predicate<String> matches) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (System.nanoTime() < deadline) {
                String message = messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (message != null && matches.test(message)) {
                    return message;
                }
            }
            return Assertions.fail("Expected gameplay display was not received.");
        }

        String awaitContaining(String text) throws InterruptedException {
            return await(message -> message.contains(text));
        }

        void awaitBoard(ChessGame game) throws InterruptedException {
            String expected = render(game);
            Assertions.assertEquals(expected, await(message -> message.contains("\u001b[")));
            Assertions.assertEquals(expected, evaluate("redraw"));
        }

        String render(ChessGame game) {
            return new BoardRenderer().render(game.getBoard(), color == null ? ChessGame.TeamColor.WHITE : color);
        }

        void assertQuiet() throws InterruptedException {
            Assertions.assertNull(messages.poll(150, TimeUnit.MILLISECONDS), "Unexpected gameplay message.");
        }

        @Override
        public void close() {
            client.close();
            facade.close();
        }
    }
}
