package service;

import chess.ChessGame;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import model.AuthData;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.exception.AlreadyTakenException;
import service.request.JoinGameRequest;
import service.result.JoinGameResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameServiceConcurrencyTests {

    @Test
    public void concurrentJoinsCannotClaimTheSameColor() throws Exception {
        BlockingGameDAO games = new BlockingGameDAO();
        MemoryAuthDAO auth = authenticatedPlayers();
        int gameID = games.createGame(newGame());
        GameLockManager locks = new GameLockManager();
        GameService firstService = new GameService(auth, games, locks);
        GameService secondService = new GameService(auth, games, locks);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<JoinGameResult> first = executor.submit(() -> firstService.joinGame(
                    new JoinGameRequest("first-token", ChessGame.TeamColor.WHITE, gameID)));
            try {
                Assertions.assertTrue(games.firstRead.await(3, TimeUnit.SECONDS));
                CountDownLatch secondStarted = new CountDownLatch(1);
                Future<JoinGameResult> second = executor.submit(() -> {
                    secondStarted.countDown();
                    return secondService.joinGame(
                            new JoinGameRequest("second-token", ChessGame.TeamColor.WHITE, gameID));
                });
                Assertions.assertTrue(secondStarted.await(3, TimeUnit.SECONDS));
                Assertions.assertThrows(TimeoutException.class,
                        () -> second.get(100, TimeUnit.MILLISECONDS));

                games.resumeFirstRead.countDown();
                first.get(3, TimeUnit.SECONDS);
                ExecutionException rejection = Assertions.assertThrows(ExecutionException.class,
                        () -> second.get(3, TimeUnit.SECONDS));
                Assertions.assertInstanceOf(AlreadyTakenException.class, rejection.getCause());
                Assertions.assertEquals("first", games.getGame(gameID).whiteUsername());
            } finally {
                games.resumeFirstRead.countDown();
            }
        }
    }

    @Test
    public void operationsOnDifferentGamesDoNotBlockEachOther() throws Exception {
        BlockingGameDAO games = new BlockingGameDAO();
        MemoryAuthDAO auth = authenticatedPlayers();
        int firstGameID = games.createGame(newGame());
        int secondGameID = games.createGame(newGame());
        GameService service = new GameService(auth, games, new GameLockManager());

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<JoinGameResult> first = executor.submit(() -> service.joinGame(
                    new JoinGameRequest("first-token", ChessGame.TeamColor.WHITE, firstGameID)));
            try {
                Assertions.assertTrue(games.firstRead.await(3, TimeUnit.SECONDS));
                executor.submit(() -> service.joinGame(
                        new JoinGameRequest("second-token", ChessGame.TeamColor.BLACK, secondGameID)))
                        .get(3, TimeUnit.SECONDS);
                Assertions.assertFalse(first.isDone());
                Assertions.assertEquals("second", games.getGame(secondGameID).blackUsername());
            } finally {
                games.resumeFirstRead.countDown();
            }
            first.get(3, TimeUnit.SECONDS);
        }
    }

    private MemoryAuthDAO authenticatedPlayers() throws Exception {
        MemoryAuthDAO auth = new MemoryAuthDAO();
        auth.createAuth(new AuthData("first-token", "first"));
        auth.createAuth(new AuthData("second-token", "second"));
        return auth;
    }

    private GameData newGame() {
        return new GameData(0, null, null, "Open Game", new ChessGame());
    }

    private static final class BlockingGameDAO extends MemoryGameDAO {
        private final CountDownLatch firstRead = new CountDownLatch(1);
        private final CountDownLatch resumeFirstRead = new CountDownLatch(1);
        private final AtomicBoolean blockNextRead = new AtomicBoolean(true);

        @Override
        public GameData getGame(int gameID) {
            GameData game = super.getGame(gameID);
            if (blockNextRead.compareAndSet(true, false)) {
                firstRead.countDown();
                try {
                    if (!resumeFirstRead.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to resume the game read.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return game;
        }
    }
}
