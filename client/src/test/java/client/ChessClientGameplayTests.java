package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ui.BoardRenderer;
import ui.EscapeSequences;
import websocket.messages.LoadGameMessage;

import java.util.Locale;
import java.util.Set;

public class ChessClientGameplayTests extends GameplayFacadeTestSupport {

    @Test
    void playersAndObserversLoadThePersistedBoardInTheirOwnPerspective() throws Exception {
        ChessGame progressed = advanceBoard();
        TestGameplayClient whitePlayer = signIn("white");
        TestGameplayClient blackPlayer = signIn("black");
        TestGameplayClient watcher = signIn("observer");
        for (TestGameplayClient client : new TestGameplayClient[]{whitePlayer, blackPlayer, watcher}) {
            client.evaluate("list");
        }

        whitePlayer.evaluate("play 1 WHITE");
        blackPlayer.evaluate("play 1 BLACK");
        watcher.evaluate("observe 1");

        BoardRenderer renderer = new BoardRenderer();
        Assertions.assertEquals("Joined 'Live Game' as WHITE." + System.lineSeparator()
                + renderer.renderWhite(progressed.getBoard()), whitePlayer.await(text -> text.startsWith("Joined")));
        Assertions.assertEquals("Joined 'Live Game' as BLACK." + System.lineSeparator()
                + renderer.render(progressed.getBoard(), ChessGame.TeamColor.BLACK),
                blackPlayer.await(text -> text.startsWith("Joined")));
        Assertions.assertEquals("Observing 'Live Game'." + System.lineSeparator()
                + renderer.renderWhite(progressed.getBoard()), watcher.await(text -> text.startsWith("Observing")));
        Assertions.assertEquals(ClientState.IN_GAME, whitePlayer.client.getState());
        Assertions.assertEquals(ClientState.IN_GAME, blackPlayer.client.getState());
        Assertions.assertEquals(ClientState.IN_GAME, watcher.client.getState());
        Assertions.assertEquals("white", games.getGame(gameID).whiteUsername());
        Assertions.assertEquals("black", games.getGame(gameID).blackUsername());
    }

    @Test
    void movesAndNotificationsReachIdleClientsWithoutAnotherCommand() throws Exception {
        TestGameplayClient whitePlayer = enter("white");
        TestGameplayClient blackPlayer = enter("black");
        TestGameplayClient watcher = enter("observer");
        Assertions.assertTrue(whitePlayer.awaitContaining("observer").contains("observer"));

        ServerFacade mover = facade(port);
        RecordingObserver moves = new RecordingObserver();
        mover.connectGame(whiteAuth.authToken(), gameID, moves);
        moves.take(LoadGameMessage.class);
        ChessMove move = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        mover.makeMove(whiteAuth.authToken(), gameID, move);

        ChessGame expected = new ChessGame();
        expected.makeMove(move);
        whitePlayer.awaitBoard(expected);
        blackPlayer.awaitBoard(expected);
        watcher.awaitBoard(expected);
        Assertions.assertTrue(watcher.await(text -> text.contains("e2") && text.contains("e4")).contains("white"));
    }

    @Test
    void reconnectAfterServerRestartRestoresTheCurrentGameWithoutJoiningAgain() throws Exception {
        TestGameplayClient player = enter("black");
        int originalPort = port;
        stopServer();
        player.awaitContaining("reconnect");
        Assertions.assertEquals(ClientState.SIGNED_IN, player.client.getState());
        ChessGame progressed = advanceBoard();
        startServer(originalPort);

        Assertions.assertEquals("Connecting to 'Live Game'...", player.evaluate("reconnect"));
        String board = player.await(text -> text.startsWith("Joined"));
        Assertions.assertTrue(board.endsWith(player.render(progressed)));
        Assertions.assertEquals(ClientState.IN_GAME, player.client.getState());
        Assertions.assertEquals("black", games.getGame(gameID).blackUsername());
    }

    @Test
    void observeWithAnExpiredSessionReturnsToSignedOut() throws Exception {
        TestGameplayClient watcher = signIn("observer");
        watcher.evaluate("list");
        auth.clear();
        watcher.evaluate("observe 1");

        Assertions.assertEquals("Your session is no longer valid. Please sign in again.",
                watcher.awaitContaining("sign in again"));
        Assertions.assertEquals(ClientState.SIGNED_OUT, watcher.client.getState());
        Assertions.assertFalse(watcher.facade.isConnected());
    }

    @Test
    void deletedGameRejectsEntryAndAllowsOtherPregameCommands() throws Exception {
        TestGameplayClient watcher = signIn("observer");
        watcher.evaluate("list");
        games.clear();
        watcher.evaluate("observe 1");

        watcher.awaitContaining("Error:");
        Assertions.assertEquals(ClientState.SIGNED_IN, watcher.client.getState());
        Assertions.assertFalse(watcher.facade.isConnected());
        Assertions.assertTrue(watcher.evaluate("create Another Game").contains("Created game"));
    }

    @ParameterizedTest
    @CsvSource({"white, e2, WHITE", "white, e7, WHITE", "black, e7, BLACK", "observer, e7, WHITE"})
    void highlightingIsLocalForPlayersAndObservers(String username, String square, ChessGame.TeamColor perspective)
            throws Exception {
        TestGameplayClient player = enter(username);
        RecordingObserver peer = new RecordingObserver();
        facade(port).connectGame(blackAuth.authToken(), gameID, peer);
        peer.take(LoadGameMessage.class);
        player.awaitContaining("black connected");
        ChessGame before = new ChessGame(games.getGame(gameID).game());
        int row = square.charAt(1) - '0';
        int step = row == 2 ? 1 : -1;

        String response = player.evaluate("HIGHLIGHT " + square.toUpperCase(Locale.ROOT));
        String expected = new BoardRenderer().renderHighlighted(before.getBoard(), perspective, new ChessPosition(row, 5),
                Set.of(new ChessPosition(row + step, 5), new ChessPosition(row + 2 * step, 5)));
        Assertions.assertTrue(response.startsWith("Selected " + square + " (yellow)."));
        Assertions.assertTrue(response.endsWith(expected));
        Assertions.assertEquals(ClientState.IN_GAME, player.client.getState());
        Assertions.assertTrue(player.evaluate("help").contains("highlight <square>"));
        Assertions.assertEquals(new BoardRenderer().render(before.getBoard(), perspective), player.evaluate("redraw"));
        Assertions.assertEquals(before, games.getGame(gameID).game());
        Assertions.assertEquals("white", games.getGame(gameID).whiteUsername());
        Assertions.assertEquals("black", games.getGame(gameID).blackUsername());
        player.assertQuiet();
        peer.assertQuiet();
    }

    @Test
    void serverUpdatesReplaceHighlightsAndSubsequentQueriesUseTheNewBoard() throws Exception {
        TestGameplayClient watcher = enter("observer");
        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);
        whiteMessages.take(LoadGameMessage.class);
        watcher.awaitContaining("white connected");
        Assertions.assertTrue(watcher.evaluate("highlight e2").contains(EscapeSequences.SET_BG_COLOR_YELLOW));
        ChessMove move = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        white.makeMove(whiteAuth.authToken(), gameID, move);
        ChessGame updated = new ChessGame();
        updated.makeMove(move);

        String board = watcher.await(text -> text.contains("\u001b["));
        Assertions.assertEquals(new BoardRenderer().renderWhite(updated.getBoard()), board);
        Assertions.assertFalse(board.contains(EscapeSequences.SET_BG_COLOR_YELLOW));
        Assertions.assertFalse(board.contains(EscapeSequences.SET_BG_COLOR_GREEN));
        Assertions.assertEquals("There is no piece at e2.", watcher.evaluate("highlight e2"));
        Assertions.assertTrue(watcher.evaluate("highlight e4").endsWith(new BoardRenderer().renderHighlighted(
                updated.getBoard(), ChessGame.TeamColor.WHITE, new ChessPosition(4, 5), Set.of(new ChessPosition(5, 5)))));
        Assertions.assertEquals(updated, games.getGame(gameID).game());
    }

    private ChessGame advanceBoard() throws Exception {
        ChessGame game = new ChessGame(games.getGame(gameID).game());
        game.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        GameData stored = games.getGame(gameID);
        games.updateGame(new GameData(gameID, stored.whiteUsername(), stored.blackUsername(), stored.gameName(), game));
        return game;
    }
}
