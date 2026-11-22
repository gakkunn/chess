package client;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessPiece;
import chess.ChessPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ui.BoardRenderer;

import java.util.Set;

public class GameBoardViewTests {

    private final GameBoardView view = new GameBoardView();

    @ParameterizedTest
    @EnumSource(ChessGame.TeamColor.class)
    void legalMovesCanBeInspectedOutsideThePiecesTurn(ChessGame.TeamColor color) {
        ChessGame game = new ChessGame();
        game.setTeamTurn(color == ChessGame.TeamColor.WHITE ? ChessGame.TeamColor.BLACK : ChessGame.TeamColor.WHITE);
        int row = color == ChessGame.TeamColor.WHITE ? 2 : 7;
        int step = color == ChessGame.TeamColor.WHITE ? 1 : -1;

        assertHighlight(game, color, "E" + row, Set.of(new ChessPosition(row + step, 5), new ChessPosition(row + 2 * step, 5)));
    }

    @Test
    void pinnedRookCannotExposeItsKingToCheck() {
        ChessGame game = emptyGame();
        put(game, 1, 5, ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KING);
        put(game, 2, 5, ChessGame.TeamColor.WHITE, ChessPiece.PieceType.ROOK);
        put(game, 8, 5, ChessGame.TeamColor.BLACK, ChessPiece.PieceType.ROOK);
        put(game, 8, 1, ChessGame.TeamColor.BLACK, ChessPiece.PieceType.KING);

        assertHighlight(game, ChessGame.TeamColor.WHITE, "e2", Set.of(new ChessPosition(3, 5), new ChessPosition(4, 5),
                new ChessPosition(5, 5), new ChessPosition(6, 5), new ChessPosition(7, 5), new ChessPosition(8, 5)));
    }

    @Test
    void kingCannotMoveIntoAnAttackedSquare() {
        ChessGame game = emptyGame();
        put(game, 1, 5, ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KING);
        put(game, 8, 5, ChessGame.TeamColor.BLACK, ChessPiece.PieceType.ROOK);
        put(game, 8, 1, ChessGame.TeamColor.BLACK, ChessPiece.PieceType.KING);

        assertHighlight(game, ChessGame.TeamColor.WHITE, "e1", Set.of(new ChessPosition(1, 4), new ChessPosition(2, 4),
                new ChessPosition(1, 6), new ChessPosition(2, 6)));
    }

    @Test
    void capturesAreIncludedAndFriendlyPiecesAreExcluded() {
        ChessGame game = emptyGame();
        put(game, 1, 1, ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KING);
        put(game, 8, 8, ChessGame.TeamColor.BLACK, ChessPiece.PieceType.KING);
        put(game, 4, 5, ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KNIGHT);
        put(game, 6, 4, ChessGame.TeamColor.BLACK, ChessPiece.PieceType.ROOK);
        put(game, 6, 6, ChessGame.TeamColor.WHITE, ChessPiece.PieceType.PAWN);

        assertHighlight(game, ChessGame.TeamColor.BLACK, "e4", Set.of(new ChessPosition(6, 4), new ChessPosition(5, 3),
                new ChessPosition(5, 7), new ChessPosition(3, 3), new ChessPosition(3, 7),
                new ChessPosition(2, 4), new ChessPosition(2, 6)));
    }

    @Test
    void promotionOptionsHighlightEachDestinationOnce() {
        ChessGame game = emptyGame();
        put(game, 1, 1, ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KING);
        put(game, 8, 8, ChessGame.TeamColor.BLACK, ChessPiece.PieceType.KING);
        put(game, 7, 5, ChessGame.TeamColor.WHITE, ChessPiece.PieceType.PAWN);
        put(game, 8, 4, ChessGame.TeamColor.BLACK, ChessPiece.PieceType.ROOK);

        assertHighlight(game, ChessGame.TeamColor.WHITE, "e7", Set.of(new ChessPosition(8, 5), new ChessPosition(8, 4)));
    }

    @Test
    void blockedPieceShowsItsSelectionAndAnExplanation() {
        ChessGame game = new ChessGame();
        String output = assertHighlight(game, ChessGame.TeamColor.WHITE, "e1", Set.of());

        Assertions.assertTrue(output.startsWith("Selected e1 (yellow). No legal moves."));
        Assertions.assertEquals(new BoardRenderer().renderWhite(game.getBoard()),
                view.evaluate("redraw", new String[]{"redraw"}, game, ChessGame.TeamColor.WHITE));
    }

    @Test
    void finishedGamesCanStillBeInspectedWithoutChangingTheirState() {
        ChessGame game = new ChessGame();
        game.endGame();

        assertHighlight(game, ChessGame.TeamColor.WHITE, "e2", Set.of(new ChessPosition(3, 5), new ChessPosition(4, 5)));
        Assertions.assertTrue(game.isGameOver());
    }

    @ParameterizedTest
    @ValueSource(strings = {"highlight", "highlight e2 extra", "highlight i2", "highlight e0", "highlight 2e", "highlight e3"})
    void invalidSelectionsHaveReadableErrorsAndLeaveTheBoardUntouched(String input) {
        ChessGame game = new ChessGame();
        ChessGame before = new ChessGame(game);
        String response = Assertions.assertDoesNotThrow(() -> view.evaluate("highlight", input.split(" "),
                game, ChessGame.TeamColor.WHITE));

        Assertions.assertFalse(response.isBlank());
        Assertions.assertFalse(response.contains("\u001b["));
        Assertions.assertEquals(before, game);
    }

    private String assertHighlight(ChessGame game, ChessGame.TeamColor perspective, String square, Set<ChessPosition> destinations) {
        ChessGame before = new ChessGame(game);
        String output = view.evaluate("highlight", new String[]{"highlight", square}, game, perspective);
        ChessPosition selected = new ChessPosition(square.charAt(1) - '0', Character.toLowerCase(square.charAt(0)) - 'a' + 1);
        String expectedBoard = new BoardRenderer().renderHighlighted(game.getBoard(), perspective, selected, destinations);

        Assertions.assertEquals(expectedBoard, output.substring(output.indexOf(System.lineSeparator()) + System.lineSeparator().length()));
        Assertions.assertEquals(before, game);
        return output;
    }

    private ChessGame emptyGame() {
        ChessGame game = new ChessGame();
        game.setBoard(new ChessBoard());
        return game;
    }

    private void put(ChessGame game, int row, int column, ChessGame.TeamColor color, ChessPiece.PieceType type) {
        game.getBoard().addPiece(new ChessPosition(row, column), new ChessPiece(color, type));
    }
}
