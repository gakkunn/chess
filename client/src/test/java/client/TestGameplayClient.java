package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import org.junit.jupiter.api.Assertions;
import ui.BoardRenderer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

final class TestGameplayClient implements AutoCloseable {

    final ChessClient client;
    final ServerFacade facade;
    final ChessGame.TeamColor color;
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    TestGameplayClient(ServerFacade facade, ChessGame.TeamColor color) {
        this.facade = facade;
        this.color = color;
        client = new ChessClient(facade);
        client.setNotificationListener(messages::add);
    }

    String evaluate(String command) {
        return client.evaluate(command);
    }

    void enter(int gameNumber) throws Exception {
        enter(color == null ? "observe " + gameNumber : "play " + gameNumber + " " + color);
    }

    void enter(String command) throws Exception {
        evaluate("list");
        evaluate(command);
        await(text -> text.startsWith("Joined") || text.startsWith("Observing"));
        Assertions.assertEquals(ClientState.IN_GAME, client.getState());
    }

    String await(Predicate<String> matches) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            String message = messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (message != null && matches.test(message)) {
                return message;
            }
        }
        return Assertions.fail("Expected gameplay display was not received.");
    }

    String awaitContaining(String text) throws InterruptedException {
        return await(message -> message.contains(text));
    }

    void awaitBoard(ChessGame game) throws InterruptedException {
        String expected = render(game);
        Assertions.assertEquals(expected, await(message -> message.contains("\u001b[")));
        Assertions.assertEquals(expected, evaluate("redraw"));
    }

    String render(ChessGame game) {
        return new BoardRenderer().render(game.getBoard(), color == null ? ChessGame.TeamColor.WHITE : color);
    }

    void assertQuiet() throws InterruptedException {
        Assertions.assertNull(messages.poll(150, TimeUnit.MILLISECONDS), "Unexpected gameplay message.");
    }

    static String command(ChessMove move) {
        String text = "move " + square(move.getStartPosition()) + " " + square(move.getEndPosition());
        return move.getPromotionPiece() == null ? text : text + " " + move.getPromotionPiece();
    }

    private static String square(ChessPosition position) {
        return "" + (char) ('a' + position.getColumn() - 1) + position.getRow();
    }

    @Override
    public void close() {
        client.close();
        facade.close();
    }
}
