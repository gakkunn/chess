package client;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ui.BoardRenderer;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class ChessClientMoveTests extends GameplayFacadeTestSupport {

    @Test
    void playersCanAlternateMovesAndEveryClientReceivesTheNewBoard() throws Exception {
        LiveClient whitePlayer = enter(white, "white", "play 1 WHITE");
        LiveClient blackPlayer = enter(black, "black", "play 1 BLACK");
        LiveClient watcher = enter(observer, "observer", "observe 1");
        ChessGame expected = new ChessGame();

        Assertions.assertEquals("Move submitted.", whitePlayer.client.evaluate("move E2 e4"));
        expected.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        awaitBoard(whitePlayer, expected, ChessGame.TeamColor.WHITE);
        awaitBoard(blackPlayer, expected, ChessGame.TeamColor.BLACK);
        awaitBoard(watcher, expected, ChessGame.TeamColor.WHITE);

        Assertions.assertEquals("Move submitted.", blackPlayer.client.evaluate("MOVE e7 E5"));
        expected.makeMove(new ChessMove(new ChessPosition(7, 5), new ChessPosition(5, 5), null));
        awaitBoard(whitePlayer, expected, ChessGame.TeamColor.WHITE);
        awaitBoard(blackPlayer, expected, ChessGame.TeamColor.BLACK);
        awaitBoard(watcher, expected, ChessGame.TeamColor.WHITE);
        Assertions.assertTrue(await(whitePlayer, text -> text.contains("e7 to e5")).contains("black"));
        Assertions.assertEquals(expected, games.getGame(gameID).game());
        Assertions.assertEquals(new BoardRenderer().render(expected.getBoard(), ChessGame.TeamColor.BLACK),
                blackPlayer.client.evaluate("redraw"));
    }

    @ParameterizedTest
    @CsvSource({"WHITE, QUEEN", "WHITE, ROOK", "WHITE, BISHOP", "WHITE, KNIGHT",
            "BLACK, QUEEN", "BLACK, ROOK", "BLACK, BISHOP", "BLACK, KNIGHT"})
    void eitherColorCanChooseAnyPromotionPiece(ChessGame.TeamColor color, ChessPiece.PieceType promotion)
            throws Exception {
        ChessBoard board = new ChessBoard();
        board.addPiece(new ChessPosition(1, 1), new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KING));
        board.addPiece(new ChessPosition(8, 8), new ChessPiece(ChessGame.TeamColor.BLACK, ChessPiece.PieceType.KING));
        int fromRow = color == ChessGame.TeamColor.WHITE ? 7 : 2;
        int toRow = color == ChessGame.TeamColor.WHITE ? 8 : 1;
        board.addPiece(new ChessPosition(fromRow, 5), new ChessPiece(color, ChessPiece.PieceType.PAWN));
        ChessGame game = new ChessGame();
        game.setBoard(board);
        game.setTeamTurn(color);
        GameData stored = games.getGame(gameID);
        games.updateGame(new GameData(gameID, stored.whiteUsername(), stored.blackUsername(), stored.gameName(), game));
        LiveClient player = enter(color == ChessGame.TeamColor.WHITE ? white : black,
                color.name().toLowerCase(Locale.ROOT), "play 1 " + color);
        observer.connectGame(observerAuth.authToken(), gameID, observerMessages);
        observerMessages.take(LoadGameMessage.class);

        Assertions.assertEquals("Move submitted.", player.client.evaluate("move E" + fromRow + " E" + toRow
                + " " + promotion.name().toLowerCase(Locale.ROOT)));
        ChessMove move = new ChessMove(new ChessPosition(fromRow, 5), new ChessPosition(toRow, 5), promotion);
        ChessGame expected = new ChessGame(game);
        expected.makeMove(move);
        awaitBoard(player, expected, color);
        ChessGame broadcast = observerMessages.take(LoadGameMessage.class).getGame();

        Assertions.assertEquals(new ChessPiece(color, promotion), broadcast.getBoard().getPiece(move.getEndPosition()));
        Assertions.assertNull(broadcast.getBoard().getPiece(move.getStartPosition()));
        Assertions.assertEquals(expected.getTeamTurn(), broadcast.getTeamTurn());
        Assertions.assertEquals(broadcast, games.getGame(gameID).game());
        Assertions.assertTrue(observerMessages.take(NotificationMessage.class).getMessage().contains("promoted to " + promotion));
    }

    @Test
    void serverRejectedMoveLeavesTheBoardAndSessionUsable() throws Exception {
        LiveClient player = enter(white, "white", "play 1 WHITE");
        observer.connectGame(observerAuth.authToken(), gameID, observerMessages);
        observerMessages.take(LoadGameMessage.class);
        String initialBoard = player.client.evaluate("redraw");

        Assertions.assertEquals("Move submitted.", player.client.evaluate("move e2 e5"));
        Assertions.assertTrue(await(player, text -> text.startsWith("Error:")).contains("not legal"));
        Assertions.assertEquals(ClientState.IN_GAME, player.client.getState());
        Assertions.assertEquals(initialBoard, player.client.evaluate("redraw"));
        Assertions.assertEquals(new ChessGame(), games.getGame(gameID).game());
        observerMessages.assertQuiet();

        player.client.evaluate("move e2 e4");
        ChessGame expected = new ChessGame();
        expected.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        awaitBoard(player, expected, ChessGame.TeamColor.WHITE);
        Assertions.assertEquals(expected, observerMessages.take(LoadGameMessage.class).getGame());
    }

    @Test
    void expiredSessionOnMoveReturnsToSignedOutWithoutChangingTheGame() throws Exception {
        LiveClient player = enter(white, "white", "play 1 WHITE");
        auth.clear();
        player.client.evaluate("move e2 e4");

        await(player, text -> text.contains("sign in again"));
        Assertions.assertEquals(ClientState.SIGNED_OUT, player.client.getState());
        Assertions.assertFalse(white.isConnected());
        Assertions.assertEquals(new ChessGame(), games.getGame(gameID).game());
    }

    private LiveClient enter(ServerFacade facade, String username, String command) throws Exception {
        ChessClient client = new ChessClient(facade);
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        client.setNotificationListener(messages::add);
        Assertions.assertEquals("Signed in as " + username + ".", client.evaluate("login " + username + " password"));
        client.evaluate("list");
        client.evaluate(command);
        LiveClient player = new LiveClient(client, messages);
        await(player, text -> text.startsWith("Joined") || text.startsWith("Observing"));
        return player;
    }

    private void awaitBoard(LiveClient player, ChessGame game, ChessGame.TeamColor perspective) throws Exception {
        String expected = new BoardRenderer().render(game.getBoard(), perspective);
        Assertions.assertEquals(expected, await(player, text -> text.equals(expected)));
        Assertions.assertEquals(expected, player.client.evaluate("redraw"));
    }

    private String await(LiveClient player, Predicate<String> matches) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            String text = player.messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (text != null && matches.test(text)) {
                return text;
            }
        }
        return Assertions.fail("Expected gameplay display was not received.");
    }

    private record LiveClient(ChessClient client, BlockingQueue<String> messages) { }
}
