package client;

import chess.ChessGame;
import chess.ChessBoard;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;
import model.AuthData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ui.BoardRenderer;
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

    @ParameterizedTest
    @CsvSource({"play 1 WHITE, WHITE", "play 1 BLACK, BLACK", "observe 1, WHITE"})
    void redrawUsesTheLatestReceivedBoardAndKeepsThePerspective(String entry, ChessGame.TeamColor perspective)
            throws Exception {
        enterGame(entry, new ChessGame());
        ChessGame updated = new ChessGame();
        updated.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        facade.current().notify(new LoadGameMessage(updated));

        Assertions.assertEquals(new BoardRenderer().render(updated.getBoard(), perspective), client.evaluate("REDRAW"));
        Assertions.assertEquals("Usage: redraw", client.evaluate("redraw extra"));
        Assertions.assertTrue(facade.moves.isEmpty());
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertEquals(!entry.startsWith("observe"), client.evaluate("help").contains("move <from> <to>"));
        Assertions.assertTrue(client.evaluate("help").contains("redraw"));
        Assertions.assertEquals("Usage: help", client.evaluate("help extra"));
    }

    @Test
    void submittedMoveDoesNotChangeTheCachedBoardBeforeTheServerResponds() throws Exception {
        enterGame("play 1 WHITE", new ChessGame());
        String initialBoard = client.evaluate("redraw");

        Assertions.assertEquals("Move submitted.", client.evaluate("MoVe E2 e4"));
        ChessMove move = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        Assertions.assertEquals(new SubmittedMove("token", 42, move), facade.moves.getFirst());
        Assertions.assertEquals(initialBoard, client.evaluate("redraw"));

        facade.current().notify(new ErrorMessage("The requested move is not legal"));
        Assertions.assertEquals(initialBoard, client.evaluate("redraw"));
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        ChessGame updated = new ChessGame();
        updated.makeMove(move);
        facade.current().notify(new LoadGameMessage(updated));
        Assertions.assertEquals(new BoardRenderer().renderWhite(updated.getBoard()), client.evaluate("redraw"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"move", "move e2", "move e2 e4 QUEEN extra", "move e0 e4", "move i2 e4",
            "move e2 e9", "move 2e e4", "move a10 a2", "move e2 e4 KING", "move e2 e4 PAWN",
            "move e2 e4 QUEEN", "move b1 c3 KNIGHT"})
    void malformedCoordinatesAndPromotionRequestsAreRejectedBeforeSending(String command) {
        enterGame("play 1 WHITE", new ChessGame());
        String initialBoard = client.evaluate("redraw");

        String response = Assertions.assertDoesNotThrow(() -> client.evaluate(command));
        Assertions.assertFalse(response.isBlank());
        Assertions.assertNotEquals("Move submitted.", response);
        Assertions.assertTrue(facade.moves.isEmpty());
        Assertions.assertEquals(initialBoard, client.evaluate("redraw"));
    }

    @ParameterizedTest
    @CsvSource({"observe 1, move e2 e4, Observers", "play 1 BLACK, move e7 e5, turn",
            "play 1 WHITE, move a3 a4, own pieces", "play 1 WHITE, move e7 e5, own pieces"})
    void onlyPlayersOnTheirTurnCanMoveTheirOwnPieces(String entry, String command, String expectedError) {
        enterGame(entry, new ChessGame());

        Assertions.assertTrue(client.evaluate(command).contains(expectedError));
        Assertions.assertTrue(facade.moves.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"WHITE, e7, e8, 7", "BLACK, e2, e1, 2"})
    void promotionRequiresAnExplicitChoice(ChessGame.TeamColor color, String from, String to, int row) {
        ChessGame game = new ChessGame();
        ChessBoard board = new ChessBoard();
        board.addPiece(new ChessPosition(row, 5), new ChessPiece(color, ChessPiece.PieceType.PAWN));
        game.setBoard(board);
        game.setTeamTurn(color);
        enterGame("play 1 " + color, game);

        Assertions.assertTrue(client.evaluate("move " + from + " " + to).contains("Choose a promotion piece"));
        Assertions.assertTrue(facade.moves.isEmpty());
    }

    @Test
    void completedGamesCanBeRedrawnButCannotBeMoved() {
        ChessGame game = new ChessGame();
        game.endGame();
        enterGame("play 1 WHITE", game);

        Assertions.assertTrue(client.evaluate("move e2 e4").contains("game is over"));
        Assertions.assertEquals(new BoardRenderer().renderWhite(game.getBoard()), client.evaluate("redraw"));
        Assertions.assertTrue(facade.moves.isEmpty());
    }

    @Test
    void failedMoveSendReturnsToPregameAndAllowsReconnection() {
        enterGame("play 1 WHITE", new ChessGame());
        facade.failSend = true;

        Assertions.assertTrue(client.evaluate("move e2 e4").contains("reconnect"));
        Assertions.assertEquals(ClientState.SIGNED_IN, client.getState());
        facade.failSend = false;
        client.evaluate("reconnect");
        facade.current().notify(new LoadGameMessage(new ChessGame()));
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertEquals(1, facade.joins);
    }

    private void enterGame(String entry, ChessGame game) {
        client.evaluate(entry);
        facade.current().notify(new LoadGameMessage(game));
        messages.clear();
    }

    private record SubmittedMove(String token, int gameID, ChessMove move) { }

    private static final class ControlledFacade extends ServerFacade {
        private final AuthData auth = new AuthData("token", "player");
        private final GameSummary game = new GameSummary(42, null, null, "Match");
        private final List<ServerMessageObserver> observers = new ArrayList<>();
        private final List<SubmittedMove> moves = new ArrayList<>();
        private boolean failConnect;
        private boolean loadImmediately;
        private boolean failSend;
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

        @Override
        public void makeMove(String token, int gameID, ChessMove move) throws ServerFacadeException {
            if (failSend) {
                throw new ServerFacadeException("Unable to send the game command to the chess server.");
            }
            moves.add(new SubmittedMove(token, gameID, move));
        }

        @Override
        public void leaveGame(String token, int gameID) {
        }

        private ServerMessageObserver current() {
            return observers.getLast();
        }
    }
}
