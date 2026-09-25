package chess;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChessBoardCopyTests {

    @Test
    public void copiedBoardHasAnIndependentCopyOfEveryPiece() {
        ChessBoard originalBoard = new ChessBoard();
        originalBoard.resetBoard();

        ChessBoard copiedBoard = new ChessBoard(originalBoard);

        Assertions.assertEquals(originalBoard, copiedBoard,
                "A copied board should have the same piece arrangement as the original board");

        ChessPosition whiteRookPosition = new ChessPosition(1, 1);
        ChessPiece originalRook = originalBoard.getPiece(whiteRookPosition);
        ChessPiece copiedRook = copiedBoard.getPiece(whiteRookPosition);

        Assertions.assertNotSame(originalRook, copiedRook,
                "Pieces on a copied board should be separate objects");
    }

    @Test
    public void changingCopiedBoardDoesNotChangeOriginalBoard() {
        ChessBoard originalBoard = new ChessBoard();
        originalBoard.resetBoard();

        ChessBoard copiedBoard = new ChessBoard(originalBoard);
        ChessPosition originalRookPosition = new ChessPosition(1, 1);
        ChessPosition emptyPosition = new ChessPosition(4, 4);

        copiedBoard.addPiece(originalRookPosition, null);
        copiedBoard.addPiece(emptyPosition,
                new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.QUEEN));

        ChessPiece originalRook = originalBoard.getPiece(originalRookPosition);
        Assertions.assertNotNull(originalRook,
                "Removing a piece from the copy should not remove it from the original board");
        Assertions.assertEquals(ChessPiece.PieceType.ROOK, originalRook.getPieceType(),
                "The original piece should remain unchanged");
        Assertions.assertNull(originalBoard.getPiece(emptyPosition),
                "Adding a piece to the copy should not add it to the original board");
        Assertions.assertNotEquals(originalBoard, copiedBoard,
                "Boards should no longer be equal after only the copy is changed");
    }
}
