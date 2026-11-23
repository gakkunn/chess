package client;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;
import chess.InvalidMoveException;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ui.BoardRenderer;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.util.Locale;
import java.util.stream.Stream;

public class ChessClientMoveTests extends GameplayFacadeTestSupport {

    private static final String STALEMATE_OPENING = "e2 e3, a7 a5, d1 h5, a8 a6, h5 a5, h7 h5, h2 h4, a6 h6, "
            + "a5 c7, f7 f6, c7 d7, e8 f7, d7 b7, d8 d3, b7 b8, d3 h7, b8 c8, f7 g6";

    @Test
    void playersCanAlternateMovesAndEveryClientReceivesTheNewBoard() throws Exception {
        TestGameplayClient whitePlayer = enter("white");
        TestGameplayClient blackPlayer = enter("black");
        TestGameplayClient watcher = enter("observer");
        ChessGame expected = new ChessGame();

        Assertions.assertEquals("Move submitted.", whitePlayer.evaluate("move E2 e4"));
        expected.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        whitePlayer.awaitBoard(expected);
        blackPlayer.awaitBoard(expected);
        watcher.awaitBoard(expected);

        Assertions.assertEquals("Move submitted.", blackPlayer.evaluate("MOVE e7 E5"));
        expected.makeMove(new ChessMove(new ChessPosition(7, 5), new ChessPosition(5, 5), null));
        whitePlayer.awaitBoard(expected);
        blackPlayer.awaitBoard(expected);
        watcher.awaitBoard(expected);
        Assertions.assertTrue(whitePlayer.awaitContaining("e7 to e5").contains("black"));
        Assertions.assertEquals(expected, games.getGame(gameID).game());
        Assertions.assertEquals(new BoardRenderer().render(expected.getBoard(), ChessGame.TeamColor.BLACK),
                blackPlayer.evaluate("redraw"));
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
        storePosition(game);
        TestGameplayClient player = enter(color.name().toLowerCase(Locale.ROOT));
        observer.connectGame(observerAuth.authToken(), gameID, observerMessages);
        observerMessages.take(LoadGameMessage.class);

        Assertions.assertEquals("Move submitted.", player.evaluate("move E" + fromRow + " E" + toRow
                + " " + promotion.name().toLowerCase(Locale.ROOT)));
        ChessMove move = new ChessMove(new ChessPosition(fromRow, 5), new ChessPosition(toRow, 5), promotion);
        ChessGame expected = new ChessGame(game);
        expected.makeMove(move);
        player.awaitBoard(expected);
        ChessGame broadcast = observerMessages.take(LoadGameMessage.class).getGame();

        Assertions.assertEquals(new ChessPiece(color, promotion), broadcast.getBoard().getPiece(move.getEndPosition()));
        Assertions.assertNull(broadcast.getBoard().getPiece(move.getStartPosition()));
        Assertions.assertEquals(expected.getTeamTurn(), broadcast.getTeamTurn());
        Assertions.assertEquals(broadcast, games.getGame(gameID).game());
        Assertions.assertTrue(observerMessages.take(NotificationMessage.class).getMessage().contains("promoted to " + promotion));
    }

    @Test
    void serverRejectedMoveLeavesTheBoardAndSessionUsable() throws Exception {
        TestGameplayClient player = enter("white");
        observer.connectGame(observerAuth.authToken(), gameID, observerMessages);
        observerMessages.take(LoadGameMessage.class);
        String initialBoard = player.evaluate("redraw");

        Assertions.assertEquals("Move submitted.", player.evaluate("move e2 e5"));
        Assertions.assertTrue(player.await(text -> text.startsWith("Error:")).contains("not legal"));
        Assertions.assertEquals(ClientState.IN_GAME, player.client.getState());
        Assertions.assertEquals(initialBoard, player.evaluate("redraw"));
        Assertions.assertEquals(new ChessGame(), games.getGame(gameID).game());
        observerMessages.assertQuiet();

        player.evaluate("move e2 e4");
        ChessGame expected = new ChessGame();
        expected.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        player.awaitBoard(expected);
        Assertions.assertEquals(expected, observerMessages.take(LoadGameMessage.class).getGame());
    }

    @Test
    void expiredSessionOnMoveReturnsToSignedOutWithoutChangingTheGame() throws Exception {
        TestGameplayClient player = enter("white");
        auth.clear();
        player.evaluate("move e2 e4");

        player.awaitContaining("sign in again");
        Assertions.assertEquals(ClientState.SIGNED_OUT, player.client.getState());
        Assertions.assertFalse(player.facade.isConnected());
        Assertions.assertEquals(new ChessGame(), games.getGame(gameID).game());
    }

    /**
     * Plays the opening, then the final move; a non-null reply answers a check so play can continue.
     */
    @ParameterizedTest
    @MethodSource("gameStatusMoves")
    void allClientsConvergeOnCheckAndTerminalGameStates(String opening, String finalMove, String reply, String notice)
            throws Exception {
        ChessGame position = playMoves(new ChessGame(), opening);
        storePosition(position);
        TestGameplayClient whitePlayer = enter("white");
        TestGameplayClient blackPlayer = enter("black");
        TestGameplayClient watcher = enter("observer");
        TestGameplayClient[] clients = {whitePlayer, blackPlayer, watcher};
        whitePlayer.awaitContaining("observer connected");
        blackPlayer.awaitContaining("observer connected");
        boolean whiteMoves = position.getTeamTurn() == ChessGame.TeamColor.WHITE;
        ChessGame expected = playMoves(new ChessGame(position), finalMove);
        if (reply == null) {
            expected.endGame();
        }

        Assertions.assertEquals("Move submitted.", (whiteMoves ? whitePlayer : blackPlayer).evaluate("move " + finalMove));
        for (TestGameplayClient client : clients) {
            client.awaitBoard(expected);
            Assertions.assertEquals(notice, client.awaitContaining(notice));
            Assertions.assertEquals(ClientState.IN_GAME, client.client.getState());
            Assertions.assertTrue(client.facade.isConnected());
        }
        Assertions.assertEquals(expected, games.getGame(gameID).game());
        if (reply == null) {
            for (TestGameplayClient player : new TestGameplayClient[]{whitePlayer, blackPlayer}) {
                Assertions.assertTrue(player.evaluate("move a1 a2").contains("game is over"));
                Assertions.assertTrue(player.evaluate("resign").contains("already over"));
            }
            return;
        }
        Assertions.assertEquals("Move submitted.", (whiteMoves ? blackPlayer : whitePlayer).evaluate("move " + reply));
        playMoves(expected, reply);
        for (TestGameplayClient client : clients) {
            client.awaitBoard(expected);
        }
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
    }

    private static Stream<Arguments> gameStatusMoves() {
        return Stream.of(
                Arguments.of("e2 e4, f7 f6", "d1 h5", "g7 g6", "black is in check."),
                Arguments.of("f2 f3, e7 e5, a2 a3", "d8 h4", "g2 g3", "white is in check."),
                Arguments.of("e2 e4, f7 f6, d2 d4, g7 g5", "d1 h5", null, "black is in checkmate. The game is over."),
                Arguments.of("f2 f3, e7 e5, g2 g4", "d8 h4", null, "white is in checkmate. The game is over."),
                Arguments.of(STALEMATE_OPENING, "c8 e6", null,
                        "Stalemate: black has no legal moves. The game is a draw."));
    }

    private static ChessGame playMoves(ChessGame game, String moves) throws InvalidMoveException {
        for (String move : moves.split(", ")) {
            game.makeMove(MoveParser.parse(("move " + move).split(" ")));
        }
        return game;
    }

    private void storePosition(ChessGame position) throws Exception {
        GameData stored = games.getGame(gameID);
        games.updateGame(new GameData(gameID, stored.whiteUsername(), stored.blackUsername(), stored.gameName(), position));
    }
}
