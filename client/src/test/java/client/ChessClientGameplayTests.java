package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ui.BoardRenderer;
import websocket.messages.LoadGameMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class ChessClientGameplayTests extends GameplayFacadeTestSupport {

    @Test
    void playersAndObserversLoadThePersistedBoardInTheirOwnPerspective() throws Exception {
        ChessGame progressed = advanceBoard();
        Player whitePlayer = player(white, "white");
        Player blackPlayer = player(black, "black");
        Player watcher = player(observer, "observer");

        whitePlayer.client.evaluate("play 1 WHITE");
        blackPlayer.client.evaluate("play 1 BLACK");
        watcher.client.evaluate("observe 1");

        BoardRenderer renderer = new BoardRenderer();
        Assertions.assertEquals("Joined 'Live Game' as WHITE." + System.lineSeparator()
                + renderer.renderWhite(progressed.getBoard()), await(whitePlayer, text -> text.startsWith("Joined")));
        Assertions.assertEquals("Joined 'Live Game' as BLACK." + System.lineSeparator()
                + renderer.render(progressed.getBoard(), ChessGame.TeamColor.BLACK),
                await(blackPlayer, text -> text.startsWith("Joined")));
        Assertions.assertEquals("Observing 'Live Game'." + System.lineSeparator()
                + renderer.renderWhite(progressed.getBoard()), await(watcher, text -> text.startsWith("Observing")));
        Assertions.assertEquals(ClientState.IN_GAME, whitePlayer.client.getState());
        Assertions.assertEquals(ClientState.IN_GAME, blackPlayer.client.getState());
        Assertions.assertEquals(ClientState.IN_GAME, watcher.client.getState());
        Assertions.assertEquals("white", games.getGame(gameID).whiteUsername());
        Assertions.assertEquals("black", games.getGame(gameID).blackUsername());
    }

    @Test
    void movesAndNotificationsReachIdleClientsWithoutAnotherCommand() throws Exception {
        Player whitePlayer = player(white, "white");
        Player blackPlayer = player(black, "black");
        Player watcher = player(observer, "observer");
        whitePlayer.client.evaluate("play 1 WHITE");
        await(whitePlayer, text -> text.startsWith("Joined"));
        blackPlayer.client.evaluate("play 1 BLACK");
        await(blackPlayer, text -> text.startsWith("Joined"));
        watcher.client.evaluate("observe 1");
        await(watcher, text -> text.startsWith("Observing"));
        Assertions.assertTrue(await(whitePlayer, text -> text.contains("observer")).contains("observer"));

        ServerFacade mover = facade(port);
        RecordingObserver moves = new RecordingObserver();
        mover.connectGame(whiteAuth.authToken(), gameID, moves);
        moves.take(LoadGameMessage.class);
        ChessMove move = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        mover.makeMove(whiteAuth.authToken(), gameID, move);

        ChessGame expected = new ChessGame();
        expected.makeMove(move);
        BoardRenderer renderer = new BoardRenderer();
        Assertions.assertEquals(renderer.renderWhite(expected.getBoard()), await(whitePlayer, text -> text.contains("\u001b[")));
        Assertions.assertEquals(renderer.render(expected.getBoard(), ChessGame.TeamColor.BLACK),
                await(blackPlayer, text -> text.contains("\u001b[")));
        Assertions.assertEquals(renderer.renderWhite(expected.getBoard()), await(watcher, text -> text.contains("\u001b[")));
        Assertions.assertTrue(await(watcher, text -> text.contains("e2") && text.contains("e4")).contains("white"));
    }

    @Test
    void reconnectAfterServerRestartRestoresTheCurrentGameWithoutJoiningAgain() throws Exception {
        Player player = player(black, "black");
        player.client.evaluate("play 1 BLACK");
        await(player, text -> text.startsWith("Joined"));
        int originalPort = port;
        stopServer();
        await(player, text -> text.contains("reconnect"));
        Assertions.assertEquals(ClientState.SIGNED_IN, player.client.getState());
        ChessGame progressed = advanceBoard();
        startServer(originalPort);

        Assertions.assertEquals("Connecting to 'Live Game'...", player.client.evaluate("reconnect"));
        String board = await(player, text -> text.startsWith("Joined"));
        Assertions.assertTrue(board.endsWith(new BoardRenderer().render(progressed.getBoard(), ChessGame.TeamColor.BLACK)));
        Assertions.assertEquals(ClientState.IN_GAME, player.client.getState());
        Assertions.assertEquals("black", games.getGame(gameID).blackUsername());
    }

    @Test
    void observeWithAnExpiredSessionReturnsToSignedOut() throws Exception {
        Player watcher = player(observer, "observer");
        auth.clear();
        watcher.client.evaluate("observe 1");

        Assertions.assertEquals("Your session is no longer valid. Please sign in again.",
                await(watcher, text -> text.contains("sign in again")));
        Assertions.assertEquals(ClientState.SIGNED_OUT, watcher.client.getState());
        Assertions.assertFalse(observer.isConnected());
    }

    @Test
    void deletedGameRejectsEntryAndAllowsOtherPregameCommands() throws Exception {
        Player watcher = player(observer, "observer");
        games.clear();
        watcher.client.evaluate("observe 1");

        await(watcher, text -> text.contains("Error:"));
        Assertions.assertEquals(ClientState.SIGNED_IN, watcher.client.getState());
        Assertions.assertFalse(observer.isConnected());
        Assertions.assertTrue(watcher.client.evaluate("create Another Game").contains("Created game"));
    }

    private ChessGame advanceBoard() throws Exception {
        ChessGame game = new ChessGame(games.getGame(gameID).game());
        game.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        GameData stored = games.getGame(gameID);
        games.updateGame(new GameData(gameID, stored.whiteUsername(), stored.blackUsername(), stored.gameName(), game));
        return game;
    }

    private Player player(ServerFacade facade, String username) {
        ChessClient client = new ChessClient(facade);
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        client.setNotificationListener(messages::add);
        Assertions.assertEquals("Signed in as " + username + ".", client.evaluate("login " + username + " password"));
        client.evaluate("list");
        return new Player(client, messages);
    }

    private String await(Player player, Predicate<String> matches) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            String message = player.messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (message != null && matches.test(message)) {
                return message;
            }
        }
        return Assertions.fail("Expected gameplay display was not received.");
    }

    private record Player(ChessClient client, BlockingQueue<String> messages) { }
}
