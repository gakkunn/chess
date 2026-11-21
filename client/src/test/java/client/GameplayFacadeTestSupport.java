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
        games = new MemoryGameDAO();
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
}
