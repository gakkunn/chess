package client;

import chess.ChessGame;
import chess.ChessMove;
import model.AuthData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GameplayExitTests {

    private final ControlledFacade facade = new ControlledFacade();
    private final ChessClient client = new ChessClient(facade);
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    @BeforeEach
    void signIn() {
        client.setNotificationListener(messages::add);
        client.evaluate("login player password");
        client.evaluate("list");
    }

    @AfterEach
    void close() {
        client.close();
        facade.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"play 1 WHITE", "play 1 BLACK", "observe 1"})
    void leaveDiscardsTheGameAndLateMessagesForEveryRole(String entry) {
        enter(entry);
        ServerMessageObserver previous = facade.observer;
        Assertions.assertEquals("Usage: leave", client.evaluate("leave extra"));
        Assertions.assertEquals("Left 'Match'.", client.evaluate("LEAVE"));

        Assertions.assertEquals(ClientState.SIGNED_IN, client.getState());
        Assertions.assertEquals(1, facade.leaves);
        Assertions.assertEquals("token", facade.lastToken);
        Assertions.assertEquals(42, facade.lastGameID);
        Assertions.assertFalse(facade.isConnected());
        Assertions.assertTrue(client.evaluate("reconnect").contains("no game"));
        previous.notify(new LoadGameMessage(new ChessGame()));
        previous.notify(new NotificationMessage("late notification"));
        previous.onDisconnect();
        Assertions.assertTrue(messages.isEmpty());
        client.close();
        Assertions.assertEquals(1, facade.leaves);
        Assertions.assertEquals(0, facade.resigns);
    }

    @Test
    void resignationRequiresConfirmationAndCanBeCancelled() {
        enter("play 1 WHITE");
        Assertions.assertEquals("Usage: resign", client.evaluate("resign yes"));
        Assertions.assertTrue(client.evaluate("yes").contains("no resignation"));
        Assertions.assertTrue(client.evaluate("resign").contains("Type 'yes'"));
        Assertions.assertTrue(client.evaluate("help").contains("Type 'yes'"));
        Assertions.assertTrue(client.evaluate("highlight e2").startsWith("Selected e2"));
        Assertions.assertTrue(client.evaluate("yes extra").contains("only 'yes' or 'no'"));
        Assertions.assertTrue(client.evaluate("move e2 e4").contains("Type 'yes'"));
        Assertions.assertEquals("Resignation cancelled.", client.evaluate("NO"));
        Assertions.assertTrue(client.evaluate("yes").contains("no resignation"));
        Assertions.assertEquals(0, facade.resigns);
        Assertions.assertEquals(0, facade.moves);
        Assertions.assertEquals("Move submitted.", client.evaluate("move e2 e4"));
    }

    @Test
    void confirmedResignationWaitsForTheServerAndKeepsTheBoardOpen() {
        enter("play 1 BLACK");
        String board = client.evaluate("redraw");
        client.evaluate("resign");
        Assertions.assertEquals("Resignation submitted.", client.evaluate("YES"));
        Assertions.assertEquals(1, facade.resigns);
        Assertions.assertEquals("token", facade.lastToken);
        Assertions.assertEquals(42, facade.lastGameID);
        Assertions.assertTrue(client.evaluate("resign").contains("Waiting"));
        Assertions.assertTrue(client.evaluate("move e7 e5").contains("Waiting"));
        Assertions.assertTrue(client.evaluate("yes").contains("no resignation"));

        facade.observer.notify(new NotificationMessage("The match has finished.", true));
        Assertions.assertEquals("The match has finished.", messages.remove());
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertTrue(facade.isConnected());
        Assertions.assertEquals(board, client.evaluate("redraw"));
        Assertions.assertTrue(client.evaluate("move e7 e5").contains("game is over"));
        Assertions.assertTrue(client.evaluate("resign").contains("already over"));
        Assertions.assertEquals(1, facade.resigns);
    }

    @Test
    void observersCannotConfirmAResignation() {
        enter("observe 1");
        Assertions.assertTrue(client.evaluate("resign").contains("Observers cannot resign"));
        Assertions.assertTrue(client.evaluate("yes").contains("no resignation"));
        Assertions.assertFalse(client.evaluate("help").contains("  resign"));
        Assertions.assertTrue(client.evaluate("help").contains("  leave"));
        Assertions.assertEquals(0, facade.resigns);
    }

    @Test
    void rejectedResignationDoesNotEndTheCachedGame() {
        enter("play 1 WHITE");
        client.evaluate("resign");
        client.evaluate("yes");
        facade.observer.notify(new ErrorMessage("Resignation rejected"));

        Assertions.assertEquals("Error: Resignation rejected", messages.remove());
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertEquals("Move submitted.", client.evaluate("move e2 e4"));
        Assertions.assertTrue(client.evaluate("resign").contains("Type 'yes'"));
    }

    @Test
    void aGameEndingDuringConfirmationCancelsThePendingResignation() {
        enter("play 1 WHITE");
        client.evaluate("resign");
        ChessGame completed = new ChessGame();
        completed.endGame();
        facade.observer.notify(new LoadGameMessage(completed));

        Assertions.assertTrue(client.evaluate("yes").contains("no resignation"));
        Assertions.assertEquals(0, facade.resigns);
    }

    @ParameterizedTest
    @ValueSource(strings = {"leave", "resign"})
    void sendFailureAllowsReconnectionWithoutKeepingConfirmation(String command) {
        enter("play 1 WHITE");
        facade.failSend = true;
        String response;
        if (command.equals("resign")) {
            client.evaluate("resign");
            response = client.evaluate("yes");
        } else {
            response = client.evaluate("leave");
        }
        Assertions.assertTrue(response.contains("reconnect"));
        Assertions.assertEquals(ClientState.SIGNED_IN, client.getState());
        Assertions.assertFalse(facade.isConnected());
        facade.failSend = false;
        client.evaluate("reconnect");
        facade.observer.notify(new LoadGameMessage(new ChessGame()));
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertTrue(client.evaluate("yes").contains("no resignation"));
        Assertions.assertEquals(1, facade.joins);
    }

    @ParameterizedTest
    @ValueSource(strings = {"quit", "close", "leave"})
    void exitingDuringConfirmationLeavesExactlyOnceWithoutResigning(String command) {
        enter("play 1 WHITE");
        client.evaluate("resign");
        if (!command.equals("close")) {
            client.evaluate(command);
        }
        client.close();
        client.close();

        Assertions.assertEquals(ClientState.QUIT, client.getState());
        Assertions.assertEquals(1, facade.leaves);
        Assertions.assertEquals(0, facade.resigns);
    }

    @Test
    void leaveCanCancelAnInitialBoardWait() {
        client.evaluate("play 1 WHITE");
        Assertions.assertEquals(ClientState.CONNECTING, client.getState());
        Assertions.assertEquals("Left 'Match'.", client.evaluate("leave"));
        facade.observer.notify(new LoadGameMessage(new ChessGame()));

        Assertions.assertEquals(ClientState.SIGNED_IN, client.getState());
        Assertions.assertEquals(1, facade.leaves);
        Assertions.assertTrue(messages.isEmpty());
    }

    @Test
    void failedDepartureStillClosesTheClientAndReportsTheFailureOnQuit() {
        enter("play 1 WHITE");
        facade.failSend = true;
        String response = client.evaluate("quit");

        Assertions.assertTrue(response.contains("Could not notify the server"));
        Assertions.assertTrue(response.endsWith("Goodbye."));
        Assertions.assertEquals(ClientState.QUIT, client.getState());
        Assertions.assertFalse(facade.isConnected());
        client.close();
        Assertions.assertEquals(1, facade.leaves);
    }

    private void enter(String command) {
        client.evaluate(command);
        facade.observer.notify(new LoadGameMessage(new ChessGame()));
        messages.clear();
    }

    private static final class ControlledFacade extends ServerFacade {
        private ServerMessageObserver observer;
        private boolean connected;
        private boolean failSend;
        private int leaves;
        private int resigns;
        private int moves;
        private int joins;
        private String lastToken;
        private int lastGameID;

        private ControlledFacade() {
            super("http://localhost:1");
        }

        @Override
        public AuthData login(String username, String password) {
            return new AuthData("token", "player");
        }

        @Override
        public List<GameSummary> listGames(String token) {
            return List.of(new GameSummary(42, null, null, "Match"));
        }

        @Override
        public void joinGame(String token, ChessGame.TeamColor color, int gameID) {
            joins++;
        }

        @Override
        public void connectGame(String token, int gameID, ServerMessageObserver listener) {
            observer = listener;
            connected = true;
        }

        @Override
        public void leaveGame(String token, int gameID) throws ServerFacadeException {
            leaves++;
            recordSend(token, gameID);
        }

        @Override
        public void resignGame(String token, int gameID) throws ServerFacadeException {
            resigns++;
            recordSend(token, gameID);
        }

        @Override
        public void makeMove(String token, int gameID, ChessMove move) {
            moves++;
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        private void recordSend(String token, int gameID) throws ServerFacadeException {
            lastToken = token;
            lastGameID = gameID;
            if (failSend) {
                throw new ServerFacadeException("Unable to send the game command.");
            }
        }
    }
}
