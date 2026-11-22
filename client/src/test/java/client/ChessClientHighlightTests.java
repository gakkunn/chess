package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ui.BoardRenderer;
import ui.EscapeSequences;
import websocket.messages.LoadGameMessage;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class ChessClientHighlightTests extends GameplayFacadeTestSupport {

    @ParameterizedTest
    @CsvSource({"white, e2, WHITE", "white, e7, WHITE", "black, e7, BLACK", "observer, e7, WHITE"})
    void highlightingIsLocalForPlayersAndObservers(String username, String square, ChessGame.TeamColor perspective)
            throws Exception {
        Player player = enter(username);
        RecordingObserver peer = new RecordingObserver();
        facade(port).connectGame(blackAuth.authToken(), gameID, peer);
        peer.take(LoadGameMessage.class);
        await(player, text -> text.contains("black connected"));
        ChessGame before = new ChessGame(games.getGame(gameID).game());
        int row = square.charAt(1) - '0';
        int step = row == 2 ? 1 : -1;

        String response = player.client.evaluate("HIGHLIGHT " + square.toUpperCase(Locale.ROOT));
        String expected = new BoardRenderer().renderHighlighted(before.getBoard(), perspective, new ChessPosition(row, 5),
                Set.of(new ChessPosition(row + step, 5), new ChessPosition(row + 2 * step, 5)));
        Assertions.assertTrue(response.startsWith("Selected " + square + " (yellow)."));
        Assertions.assertTrue(response.endsWith(expected));
        Assertions.assertEquals(ClientState.IN_GAME, player.client.getState());
        Assertions.assertTrue(player.client.evaluate("help").contains("highlight <square>"));
        Assertions.assertEquals(new BoardRenderer().render(before.getBoard(), perspective), player.client.evaluate("redraw"));
        Assertions.assertEquals(before, games.getGame(gameID).game());
        Assertions.assertEquals("white", games.getGame(gameID).whiteUsername());
        Assertions.assertEquals("black", games.getGame(gameID).blackUsername());
        Assertions.assertNull(player.messages.poll(150, TimeUnit.MILLISECONDS));
        peer.assertQuiet();
    }

    @Test
    void serverUpdatesReplaceHighlightsAndSubsequentQueriesUseTheNewBoard() throws Exception {
        Player watcher = enter("observer");
        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);
        whiteMessages.take(LoadGameMessage.class);
        await(watcher, text -> text.contains("white connected"));
        Assertions.assertTrue(watcher.client.evaluate("highlight e2").contains(EscapeSequences.SET_BG_COLOR_YELLOW));
        ChessMove move = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        white.makeMove(whiteAuth.authToken(), gameID, move);
        ChessGame updated = new ChessGame();
        updated.makeMove(move);

        String board = await(watcher, text -> text.contains("\u001b["));
        Assertions.assertEquals(new BoardRenderer().renderWhite(updated.getBoard()), board);
        Assertions.assertFalse(board.contains(EscapeSequences.SET_BG_COLOR_YELLOW));
        Assertions.assertFalse(board.contains(EscapeSequences.SET_BG_COLOR_GREEN));
        Assertions.assertEquals("There is no piece at e2.", watcher.client.evaluate("highlight e2"));
        Assertions.assertTrue(watcher.client.evaluate("highlight e4").endsWith(new BoardRenderer().renderHighlighted(
                updated.getBoard(), ChessGame.TeamColor.WHITE, new ChessPosition(4, 5), Set.of(new ChessPosition(5, 5)))));
        Assertions.assertEquals(updated, games.getGame(gameID).game());
    }

    private Player enter(String username) throws Exception {
        ServerFacade facade = switch (username) {
            case "white" -> white;
            case "black" -> black;
            default -> observer;
        };
        ChessClient client = new ChessClient(facade);
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        client.setNotificationListener(messages::add);
        Assertions.assertEquals("Signed in as " + username + ".", client.evaluate("login " + username + " password"));
        client.evaluate("list");
        client.evaluate(username.equals("observer") ? "observe 1" : "play 1 " + username);
        Player player = new Player(client, messages);
        await(player, text -> text.startsWith("Joined") || text.startsWith("Observing"));
        return player;
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
