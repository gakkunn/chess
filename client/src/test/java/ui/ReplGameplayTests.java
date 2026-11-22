package ui;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import client.ChessClient;
import client.ClientState;
import client.ServerFacade;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import model.AuthData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.Server;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ReplGameplayTests {

    private Server server;
    private MemoryGameDAO games;
    private ServerFacade player;
    private ServerFacade clientFacade;
    private ChessClient client;
    private PipedOutputStream keyboard;
    private Scanner scanner;
    private PrintStream terminal;
    private final ObservedOutput output = new ObservedOutput();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> repl;
    private AuthData playerAuth;
    private int gameID;

    @BeforeEach
    void openTerminal() throws Exception {
        games = new MemoryGameDAO();
        server = new Server(new MemoryUserDAO(), new MemoryAuthDAO(), games);
        int port = server.run(0);
        player = new ServerFacade(port);
        playerAuth = player.register("white", "password", "white@example.com");
        player.register("watcher", "password", "watcher@example.com");
        gameID = player.createGame(playerAuth.authToken(), "Terminal Match");
        player.joinGame(playerAuth.authToken(), ChessGame.TeamColor.WHITE, gameID);
        clientFacade = new ServerFacade(port);
        client = new ChessClient(clientFacade);
        PipedInputStream input = new PipedInputStream();
        keyboard = new PipedOutputStream(input);
        scanner = new Scanner(input, StandardCharsets.UTF_8);
        terminal = new PrintStream(output, true, StandardCharsets.UTF_8);
        repl = executor.submit(() -> new Repl(client, scanner, terminal).run());
    }

    @AfterEach
    void closeTerminal() throws Exception {
        keyboard.close();
        try {
            repl.get(4, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            scanner.close();
            terminal.close();
            client.close();
            clientFacade.close();
            player.close();
            server.stop();
        }
    }

    @Test
    void notificationsAndBoardUpdatesDisplayWhileWaitingForKeyboardInput() throws Exception {
        type("login watcher password\nlist\nobserve 1\n");
        output.await("Observing 'Terminal Match'.", 0);
        output.await("[IN_GAME] >>> ", 0);
        int beforeConnect = output.size();
        BlockingQueue<ServerMessage> messages = new LinkedBlockingQueue<>();
        player.connectGame(playerAuth.authToken(), gameID, messages::add);
        Assertions.assertInstanceOf(LoadGameMessage.class, messages.poll(3, TimeUnit.SECONDS));
        output.await("white connected as a WHITE player.", beforeConnect);

        int beforeMove = output.size();
        ChessMove move = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        player.makeMove(playerAuth.authToken(), gameID, move);
        ChessGame expected = new ChessGame();
        expected.makeMove(move);
        output.await(new BoardRenderer().renderWhite(expected.getBoard()), beforeMove);
        output.await("e2 to e4", beforeMove);
        output.await("[IN_GAME] >>> ", beforeMove);
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());

        type("quit\n");
        repl.get(4, TimeUnit.SECONDS);
        Assertions.assertTrue(output.toString(StandardCharsets.UTF_8).contains("Goodbye."));
        Assertions.assertFalse(clientFacade.isConnected());
    }

    @Test
    void endOfInputLeavesWithoutConfirmingAPendingResignation() throws Exception {
        type("login white password\nlist\nplay 1 WHITE\n");
        output.await("Joined 'Terminal Match' as WHITE.", 0);
        Assertions.assertTrue(clientFacade.isConnected());
        BlockingQueue<ServerMessage> messages = new LinkedBlockingQueue<>();
        player.connectGame(playerAuth.authToken(), gameID, messages::add);
        Assertions.assertInstanceOf(LoadGameMessage.class, messages.poll(3, TimeUnit.SECONDS));
        type("resign\n");
        output.await("Type 'yes' to confirm", 0);

        keyboard.close();
        repl.get(4, TimeUnit.SECONDS);
        NotificationMessage notice = Assertions.assertInstanceOf(NotificationMessage.class, messages.poll(3, TimeUnit.SECONDS));
        Assertions.assertEquals("white left the game.", notice.getMessage());
        Assertions.assertNull(games.getGame(gameID).whiteUsername());
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
        Assertions.assertEquals(ClientState.QUIT, client.getState());
        Assertions.assertFalse(clientFacade.isConnected());
    }

    @Test
    void terminalCanCancelConfirmAndLeaveAfterResignation() throws Exception {
        type("login white password\nlist\nplay 1 WHITE\n");
        output.await("[IN_GAME] >>> ", 0);
        BlockingQueue<ServerMessage> messages = new LinkedBlockingQueue<>();
        player.connectGame(playerAuth.authToken(), gameID, messages::add);
        Assertions.assertInstanceOf(LoadGameMessage.class, messages.poll(3, TimeUnit.SECONDS));
        int beforeConfirmation = output.size();

        type("resign\n");
        output.await("Type 'yes' to confirm", beforeConfirmation);
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
        type("no\n");
        output.await("Resignation cancelled.", beforeConfirmation);
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
        type("resign\nyes\n");
        output.await("white resigned. The game is over.", beforeConfirmation);
        Assertions.assertTrue(games.getGame(gameID).game().isGameOver());
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        Assertions.assertTrue(clientFacade.isConnected());

        int beforeLeaving = output.size();
        type("redraw\nleave\n");
        output.await("Left 'Terminal Match'.", beforeLeaving);
        output.await("[SIGNED_IN] >>> ", beforeLeaving);
        Assertions.assertTrue(Assertions.assertInstanceOf(NotificationMessage.class,
                messages.poll(3, TimeUnit.SECONDS)).isGameOver());
        Assertions.assertEquals("white left the game.", Assertions.assertInstanceOf(NotificationMessage.class,
                messages.poll(3, TimeUnit.SECONDS)).getMessage());
        Assertions.assertNull(games.getGame(gameID).whiteUsername());
        Assertions.assertEquals(ClientState.SIGNED_IN, client.getState());
        type("quit\n");
        repl.get(4, TimeUnit.SECONDS);
    }

    @Test
    void endOfInputImmediatelyAfterPlayStillReleasesTheSeat() throws Exception {
        BlockingQueue<ServerMessage> messages = new LinkedBlockingQueue<>();
        player.connectGame(playerAuth.authToken(), gameID, messages::add);
        Assertions.assertInstanceOf(LoadGameMessage.class, messages.poll(3, TimeUnit.SECONDS));

        type("login white password\nlist\nplay 1 WHITE\n");
        keyboard.close();
        repl.get(4, TimeUnit.SECONDS);
        String lastNotice = "";
        for (int index = 0; index < 2 && !lastNotice.equals("white left the game."); index++) {
            NotificationMessage notice = Assertions.assertInstanceOf(NotificationMessage.class, messages.poll(3, TimeUnit.SECONDS));
            lastNotice = notice.getMessage();
        }
        Assertions.assertEquals("white left the game.", lastNotice);
        Assertions.assertNull(games.getGame(gameID).whiteUsername());
        Assertions.assertEquals(ClientState.QUIT, client.getState());
        Assertions.assertFalse(clientFacade.isConnected());
    }

    @Test
    void terminalAcceptsHelpRedrawAndMoveCommandsDuringGameplay() throws Exception {
        type("login white password\nlist\nplay 1 WHITE\n");
        output.await("[IN_GAME] >>> ", 0);
        int beforeCommands = output.size();

        type("help\nredraw\nmove E2 e4\n");
        output.await("move <from> <to>", beforeCommands);
        output.await(new BoardRenderer().renderWhite(new ChessGame().getBoard()), beforeCommands);
        output.await("Move submitted.", beforeCommands);
        ChessGame updated = new ChessGame();
        updated.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        output.await(new BoardRenderer().renderWhite(updated.getBoard()), beforeCommands);

        int beforeInvalid = output.size();
        type("move i2 e4\n");
        output.await("Use board squares a1 through h8", beforeInvalid);
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
        type("quit\n");
        repl.get(4, TimeUnit.SECONDS);
    }

    private void type(String commands) throws Exception {
        keyboard.write(commands.getBytes(StandardCharsets.UTF_8));
        keyboard.flush();
    }

    private static final class ObservedOutput extends ByteArrayOutputStream {
        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            super.write(bytes, offset, length);
            notifyAll();
        }

        @Override
        public synchronized void write(int value) {
            super.write(value);
            notifyAll();
        }

        private synchronized void await(String text, int start) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (!new String(buf, start, count - start, StandardCharsets.UTF_8).contains(text)) {
                long remaining = deadline - System.nanoTime();
                Assertions.assertTrue(remaining > 0, "Expected terminal output was not displayed: " + text);
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
        }
    }
}
