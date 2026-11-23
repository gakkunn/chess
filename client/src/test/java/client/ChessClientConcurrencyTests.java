package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import dataaccess.DataAccessException;
import dataaccess.MemoryGameDAO;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class ChessClientConcurrencyTests extends GameplayFacadeTestSupport {

    private PausingGameDAO controlledGames;

    @Override
    protected MemoryGameDAO createGameDAO() {
        controlledGames = new PausingGameDAO();
        return controlledGames;
    }

    @Test
    void competingMovesFromTheSamePlayerConvergeWithoutAnEarlyBoardUpdate() throws Exception {
        TestGameplayClient first = enter("white");
        TestGameplayClient second = enter("white");
        TestGameplayClient blackPlayer = enter("black");
        TestGameplayClient watcher = enter("observer");
        for (TestGameplayClient client : new TestGameplayClient[]{first, second, blackPlayer}) {
            client.awaitContaining("observer connected");
        }
        SaveGate save = controlledGames.pause(gameID);
        try {
            Assertions.assertEquals("Move submitted.", first.evaluate("move e2 e4"));
            save.awaitStarted();
            Assertions.assertEquals("Move submitted.", second.evaluate("move d2 d4"));
            Assertions.assertEquals(new ChessGame(), games.getGame(gameID).game());
            for (TestGameplayClient client : new TestGameplayClient[]{first, second, blackPlayer, watcher}) {
                Assertions.assertEquals(client.render(new ChessGame()), client.evaluate("redraw"));
                client.assertQuiet();
            }
        } finally {
            save.release();
        }
        ChessGame expected = afterWhiteMove();
        for (TestGameplayClient client : new TestGameplayClient[]{first, second, blackPlayer, watcher}) {
            client.awaitBoard(expected);
        }
        Assertions.assertTrue(second.awaitContaining("Error:").contains("not your turn"));
        Assertions.assertEquals(expected, games.getGame(gameID).game());
        blackPlayer.evaluate("move e7 e5");
        expected.makeMove(move(7, 5, 5, 5));
        for (TestGameplayClient client : new TestGameplayClient[]{first, second, blackPlayer, watcher}) {
            client.awaitBoard(expected);
        }
    }

    @Test
    void resignationFollowingAPendingMoveCannotRestoreAnOlderBoard() throws Exception {
        TestGameplayClient[] clients = connectAll();
        SaveGate save = controlledGames.pause(gameID);
        try {
            clients[0].evaluate("move e2 e4");
            save.awaitStarted();
            clients[1].evaluate("resign");
            Assertions.assertEquals("Resignation submitted.", clients[1].evaluate("yes"));
            Assertions.assertEquals(new ChessGame(), games.getGame(gameID).game());
            for (TestGameplayClient client : clients) {
                client.assertQuiet();
            }
        } finally {
            save.release();
        }
        ChessGame expected = afterWhiteMove();
        for (TestGameplayClient client : clients) {
            client.awaitBoard(expected);
            client.awaitContaining("black resigned");
            Assertions.assertEquals(ClientState.IN_GAME, client.client.getState());
        }
        expected.endGame();
        Assertions.assertEquals(expected, games.getGame(gameID).game());
        Assertions.assertTrue(clients[0].evaluate("move d2 d4").contains("game is over"));
        Assertions.assertTrue(clients[1].evaluate("move e7 e5").contains("game is over"));
    }

    @Test
    void aMoveQueuedBehindResignationIsRejectedWithoutChangingAnyBoard() throws Exception {
        TestGameplayClient[] clients = connectAll();
        SaveGate save = controlledGames.pause(gameID);
        try {
            clients[1].evaluate("resign");
            clients[1].evaluate("yes");
            save.awaitStarted();
            Assertions.assertEquals("Move submitted.", clients[0].evaluate("move e2 e4"));
            Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
            for (TestGameplayClient client : clients) {
                client.assertQuiet();
            }
        } finally {
            save.release();
        }
        ChessGame expected = new ChessGame();
        expected.endGame();
        for (TestGameplayClient client : clients) {
            client.awaitContaining("black resigned");
            Assertions.assertEquals(client.render(expected), client.evaluate("redraw"));
        }
        Assertions.assertTrue(clients[0].awaitContaining("Error:").contains("game is over"));
        Assertions.assertEquals(expected, games.getGame(gameID).game());
    }

    @Test
    void replacementJoinsWaitForDepartureAndRetainTheCurrentPosition() throws Exception {
        TestGameplayClient[] clients = connectAll();
        clients[0].evaluate("move e2 e4");
        ChessGame expected = afterWhiteMove();
        for (TestGameplayClient client : clients) {
            client.awaitBoard(expected);
        }
        clients[1].awaitContaining("e2 to e4");
        clients[2].awaitContaining("e2 to e4");
        TestGameplayClient replacement = new TestGameplayClient(facade(port), ChessGame.TeamColor.WHITE);
        replacement.evaluate("register replacement password replacement@example.com");
        replacement.evaluate("list");
        SaveGate save = controlledGames.pause(gameID);
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                Assertions.assertEquals("Left 'Live Game'.", clients[0].evaluate("leave"));
                save.awaitStarted();
                CountDownLatch joining = new CountDownLatch(1);
                var result = executor.submit(() -> {
                    joining.countDown();
                    return replacement.evaluate("play 1 WHITE");
                });
                Assertions.assertTrue(joining.await(3, TimeUnit.SECONDS));
                Assertions.assertThrows(TimeoutException.class, () -> result.get(150, TimeUnit.MILLISECONDS));
                Assertions.assertEquals("white", games.getGame(gameID).whiteUsername());
                clients[1].assertQuiet();
                clients[2].assertQuiet();
                save.release();
                Assertions.assertTrue(result.get(4, TimeUnit.SECONDS).contains("Connecting"));
                replacement.await(text -> text.startsWith("Joined"));
                Assertions.assertEquals(replacement.render(expected), replacement.evaluate("redraw"));
                clients[1].awaitContaining("replacement connected");
                clients[2].awaitContaining("replacement connected");
                Assertions.assertEquals("replacement", games.getGame(gameID).whiteUsername());
                Assertions.assertEquals(expected, games.getGame(gameID).game());
                clients[1].evaluate("move e7 e5");
                expected.makeMove(move(7, 5, 5, 5));
                replacement.awaitBoard(expected);
                clients[1].awaitBoard(expected);
                clients[2].awaitBoard(expected);
            } finally {
                save.release();
            }
        }
    }

    @Test
    void aPendingSaveDoesNotBlockOrBroadcastIntoAnotherGame() throws Exception {
        int otherID = white.createGame(whiteAuth.authToken(), "Independent Game");
        white.joinGame(whiteAuth.authToken(), ChessGame.TeamColor.WHITE, otherID);
        TestGameplayClient first = enter("white");
        TestGameplayClient other = enter("white", 2);
        SaveGate save = controlledGames.pause(gameID);
        try {
            first.evaluate("move e2 e4");
            save.awaitStarted();
            Assertions.assertEquals("Move submitted.", other.evaluate("move d2 d4"));
            ChessGame expected = new ChessGame();
            expected.makeMove(move(2, 4, 4, 4));
            other.awaitBoard(expected);
            Assertions.assertEquals(expected, games.getGame(otherID).game());
            Assertions.assertEquals(new ChessGame(), games.getGame(gameID).game());
            first.assertQuiet();
        } finally {
            save.release();
        }
        first.awaitBoard(afterWhiteMove());
        other.assertQuiet();
    }

    @Test
    void aSlowDisplayCallbackDoesNotLockBoardQueriesOrLoseTheUpdate() throws Exception {
        TestGameplayClient[] clients = connectAll();
        CountDownLatch displaying = new CountDownLatch(1);
        CountDownLatch resumeDisplay = new CountDownLatch(1);
        TestGameplayClient watcher = clients[2];
        watcher.client.setNotificationListener(message -> {
            if (message.contains("\u001b[")) {
                displaying.countDown();
                try {
                    if (!resumeDisplay.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to resume the display.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            watcher.messages.add(message);
        });
        ChessGame expected = afterWhiteMove();
        try (var executor = Executors.newSingleThreadExecutor()) {
            try {
                clients[0].evaluate("move e2 e4");
                Assertions.assertTrue(displaying.await(3, TimeUnit.SECONDS));
                Assertions.assertEquals(watcher.render(expected), executor.submit(() -> watcher.evaluate("redraw"))
                        .get(3, TimeUnit.SECONDS));
                Assertions.assertTrue(executor.submit(() -> watcher.evaluate("highlight e4"))
                        .get(3, TimeUnit.SECONDS).startsWith("Selected e4"));
            } finally {
                resumeDisplay.countDown();
            }
        }
        for (TestGameplayClient client : clients) {
            client.awaitBoard(expected);
        }
    }

    private TestGameplayClient[] connectAll() throws Exception {
        TestGameplayClient whitePlayer = enter("white");
        TestGameplayClient blackPlayer = enter("black");
        TestGameplayClient watcher = enter("observer");
        whitePlayer.awaitContaining("observer connected");
        blackPlayer.awaitContaining("observer connected");
        return new TestGameplayClient[]{whitePlayer, blackPlayer, watcher};
    }

    private ChessGame afterWhiteMove() throws Exception {
        ChessGame game = new ChessGame();
        game.makeMove(move(2, 5, 4, 5));
        return game;
    }

    private ChessMove move(int fromRow, int fromColumn, int toRow, int toColumn) {
        return new ChessMove(new ChessPosition(fromRow, fromColumn), new ChessPosition(toRow, toColumn), null);
    }

    private static final class SaveGate {
        private final int gameID;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        private SaveGate(int gameID) {
            this.gameID = gameID;
        }

        private void awaitStarted() throws InterruptedException {
            Assertions.assertTrue(started.await(3, TimeUnit.SECONDS), "The game save did not start.");
        }

        private void release() {
            resume.countDown();
        }
    }

    private static final class PausingGameDAO extends MemoryGameDAO {
        private final AtomicReference<SaveGate> pending = new AtomicReference<>();

        private SaveGate pause(int gameID) {
            SaveGate gate = new SaveGate(gameID);
            pending.set(gate);
            return gate;
        }

        @Override
        public void updateGame(GameData data) throws DataAccessException {
            SaveGate gate = pending.get();
            if (gate != null && gate.gameID == data.gameID() && pending.compareAndSet(gate, null)) {
                gate.started.countDown();
                try {
                    if (!gate.resume.await(8, TimeUnit.SECONDS)) {
                        throw new DataAccessException("Timed out waiting to resume the game save.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new DataAccessException("The game save was interrupted.");
                }
            }
            super.updateGame(data);
        }
    }
}
