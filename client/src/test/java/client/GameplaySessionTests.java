package client;

import chess.ChessGame;
import model.AuthData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class GameplaySessionTests {

    private ControlledFacade facade;
    private ChessClient client;
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    @BeforeEach
    void signIn() {
        facade = new ControlledFacade();
        client = new ChessClient(facade);
        client.setNotificationListener(messages::add);
        client.evaluate("login player password");
        client.evaluate("list");
    }

    @AfterEach
    void close() {
        client.close();
        facade.close();
    }

    @Test
    void entryWaitsForTheServerBoardAndRestrictsPregameCommands() {
        Assertions.assertEquals("Connecting to 'Match'...", client.evaluate("play 1 WHITE"));
        Assertions.assertEquals(ClientState.CONNECTING, client.getState());
        Assertions.assertTrue(messages.isEmpty(), "No board may be fabricated before LOAD_GAME.");
        Assertions.assertTrue(client.evaluate("list").contains("Waiting"));
        Assertions.assertFalse(client.evaluate("help").contains("logout"));

        facade.current().notify(new LoadGameMessage(new ChessGame()));
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertTrue(messages.remove().startsWith("Joined 'Match' as WHITE."));
        Assertions.assertFalse(client.evaluate("help").contains("login"));
        Assertions.assertTrue(client.evaluate("logout").contains("Unknown command"));
    }

    @Test
    void failedWebSocketCanReconnectWithoutRepeatingTheHttpJoin() {
        facade.failConnect = true;
        Assertions.assertTrue(client.evaluate("play 1 WHITE").contains("reconnect"));
        Assertions.assertEquals(ClientState.SIGNED_IN, client.getState());
        Assertions.assertEquals(1, facade.joins);
        facade.failConnect = false;

        client.evaluate("reconnect");
        facade.current().notify(new LoadGameMessage(new ChessGame()));
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertEquals(1, facade.joins);
    }

    @Test
    void messagesFromAFailedAttemptCannotChangeAReconnectedGame() {
        client.evaluate("observe 1");
        ServerMessageObserver old = facade.current();
        old.notify(new ErrorMessage("bad request"));
        Assertions.assertEquals(ClientState.SIGNED_IN, client.getState());
        messages.clear();
        client.evaluate("reconnect");

        old.notify(new LoadGameMessage(new ChessGame()));
        old.notify(new NotificationMessage("stale notice"));
        old.onDisconnect();
        Assertions.assertEquals(ClientState.CONNECTING, client.getState());
        Assertions.assertTrue(messages.isEmpty());
        facade.current().notify(new LoadGameMessage(new ChessGame()));
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertEquals(0, facade.joins);
    }

    @Test
    void rejectedGameplayActionKeepsTheGameOpen() {
        client.evaluate("play 1 WHITE");
        facade.current().notify(new LoadGameMessage(new ChessGame()));
        messages.clear();

        facade.current().notify(new ErrorMessage("It is not your turn"));
        Assertions.assertEquals("Error: It is not your turn", messages.remove());
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
    }

    @Test
    void boardReceivedBeforeConnectReturnsStillEntersGameplay() {
        facade.loadImmediately = true;
        client.evaluate("observe 1");

        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertTrue(messages.remove().startsWith("Observing 'Match'."));
    }

    @Test
    void receivingTheBoardCancelsTheInitialLoadTimeout() throws Exception {
        GameplaySession session = new GameplaySession(facade, messages::add, token -> Assertions.fail("Unexpected expiry"),
                Duration.ofMillis(100));
        session.connect(facade.auth, facade.game, ChessGame.TeamColor.WHITE);
        facade.current().notify(new LoadGameMessage(new ChessGame()));
        messages.remove();

        Assertions.assertNull(messages.poll(200, TimeUnit.MILLISECONDS));
        Assertions.assertEquals(ClientState.IN_GAME, session.getState());
        session.reset();
    }

    @Test
    void initialBoardTimeoutReturnsToPregameAndIgnoresLateMessages() throws Exception {
        GameplaySession session = new GameplaySession(facade, messages::add, token -> Assertions.fail("Unexpected expiry"),
                Duration.ofMillis(100));
        session.connect(facade.auth, facade.game, ChessGame.TeamColor.BLACK);
        ServerMessageObserver late = facade.current();
        String timeout = messages.poll(3, TimeUnit.SECONDS);

        Assertions.assertNotNull(timeout);
        Assertions.assertTrue(timeout.contains("in time"));
        Assertions.assertEquals(ClientState.SIGNED_IN, session.getState());
        late.notify(new LoadGameMessage(new ChessGame()));
        Assertions.assertTrue(messages.isEmpty());
        session.reconnect();
        facade.current().notify(new LoadGameMessage(new ChessGame()));
        Assertions.assertEquals(ClientState.IN_GAME, session.getState());
        session.reset();
    }

    @Test
    void quitDiscardsLaterGameMessages() {
        client.evaluate("observe 1");
        ServerMessageObserver old = facade.current();
        Assertions.assertEquals("Goodbye.", client.evaluate("quit"));
        old.notify(new LoadGameMessage(new ChessGame()));
        old.notify(new NotificationMessage("late notice"));
        old.onDisconnect();

        Assertions.assertEquals(ClientState.QUIT, client.getState());
        Assertions.assertTrue(messages.isEmpty());
    }

    @Test
    void reconnectRequiresAPreviousGameAndValidArguments() {
        Assertions.assertTrue(client.evaluate("reconnect").contains("no game"));
        Assertions.assertEquals("Usage: reconnect", client.evaluate("reconnect extra"));
    }

    private static final class ControlledFacade extends ServerFacade {
        private final AuthData auth = new AuthData("token", "player");
        private final GameSummary game = new GameSummary(42, null, null, "Match");
        private final List<ServerMessageObserver> observers = new ArrayList<>();
        private boolean failConnect;
        private boolean loadImmediately;
        private int joins;

        private ControlledFacade() {
            super("http://localhost:1");
        }

        @Override
        public AuthData login(String username, String password) {
            return auth;
        }

        @Override
        public List<GameSummary> listGames(String token) {
            return List.of(game);
        }

        @Override
        public void joinGame(String token, ChessGame.TeamColor color, int gameID) {
            joins++;
        }

        @Override
        public void connectGame(String token, int gameID, ServerMessageObserver observer) throws ServerFacadeException {
            observers.add(observer);
            if (failConnect) {
                throw new ServerFacadeException("Unable to connect.");
            }
            if (loadImmediately) {
                observer.notify(new LoadGameMessage(new ChessGame()));
            }
        }

        private ServerMessageObserver current() {
            return observers.getLast();
        }
    }
}
