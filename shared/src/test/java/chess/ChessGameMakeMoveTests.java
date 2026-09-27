package chess;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChessGameMakeMoveTests {

    @Test
    public void rejectedMoveDoesNotChangeBoardOrTurn() {
        ChessGame game = new ChessGame();
        ChessBoard boardBeforeMove = new ChessBoard(game.getBoard());
        ChessGame.TeamColor turnBeforeMove = game.getTeamTurn();

        ChessMove illegalMove = new ChessMove(
                new ChessPosition(2, 1),
                new ChessPosition(5, 1),
                null);

        Assertions.assertThrows(InvalidMoveException.class,
                () -> game.makeMove(illegalMove),
                "Moving a pawn three squares should be rejected");

        Assertions.assertEquals(boardBeforeMove, game.getBoard(),
                "A rejected move should not change the board");
        Assertions.assertEquals(turnBeforeMove, game.getTeamTurn(),
                "A rejected move should not change the current turn");
    }

    @Test
    public void pawnMustChoosePieceWhenPromoting() {
        ChessGame game = createGameWithPawnReadyToPromote();
        ChessMove promotionWithoutPiece = new ChessMove(
                new ChessPosition(7, 3),
                new ChessPosition(8, 3),
                null);

        Assertions.assertThrows(InvalidMoveException.class,
                () -> game.makeMove(promotionWithoutPiece),
                "A pawn reaching the final row must select a promotion piece");
    }

    @Test
    public void pawnCannotPromoteToKing() {
        ChessGame game = createGameWithPawnReadyToPromote();
        ChessMove promotionToKing = new ChessMove(
                new ChessPosition(7, 3),
                new ChessPosition(8, 3),
                ChessPiece.PieceType.KING);

        Assertions.assertThrows(InvalidMoveException.class,
                () -> game.makeMove(promotionToKing),
                "A pawn cannot promote to a king");
    }

    @Test
    public void nonPawnMoveCannotRequestPromotion() {
        ChessGame game = new ChessGame();
        ChessMove knightMoveWithPromotion = new ChessMove(
                new ChessPosition(1, 2),
                new ChessPosition(3, 3),
                ChessPiece.PieceType.QUEEN);

        Assertions.assertThrows(InvalidMoveException.class,
                () -> game.makeMove(knightMoveWithPromotion),
                "Only a pawn reaching the final row can request promotion");
    }

    private ChessGame createGameWithPawnReadyToPromote() {
        ChessBoard board = new ChessBoard();
        board.addPiece(new ChessPosition(1, 1),
                new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KING));
        board.addPiece(new ChessPosition(8, 8),
                new ChessPiece(ChessGame.TeamColor.BLACK, ChessPiece.PieceType.KING));
        board.addPiece(new ChessPosition(7, 3),
                new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.PAWN));

        ChessGame game = new ChessGame();
        game.setBoard(board);
        game.setTeamTurn(ChessGame.TeamColor.WHITE);
        return game;
    }
}
