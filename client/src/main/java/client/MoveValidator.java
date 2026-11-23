package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;

final class MoveValidator {

    private MoveValidator() {
    }

    /**
     * Checks the rules the client can verify locally; the server still decides whether the move is legal.
     *
     * @return a message explaining why the move cannot be sent, or null if it can be sent
     */
    static String validate(ChessGame game, ChessGame.TeamColor color, ChessMove move) {
        if (color == null) {
            return "Observers cannot make moves.";
        }
        if (game.isGameOver()) {
            return "The game is over. You can still redraw the board.";
        }
        if (game.getTeamTurn() != color) {
            return "It is not your turn.";
        }
        ChessPiece piece = game.getBoard().getPiece(move.getStartPosition());
        if (piece == null || piece.getTeamColor() != color) {
            return "Choose one of your own pieces.";
        }
        boolean promotes = piece.getPieceType() == ChessPiece.PieceType.PAWN
                && move.getEndPosition().getRow() == (color == ChessGame.TeamColor.WHITE ? 8 : 1);
        if (promotes && move.getPromotionPiece() == null) {
            return "Choose a promotion piece: move " + MoveParser.formatPosition(move.getStartPosition())
                    + " " + MoveParser.formatPosition(move.getEndPosition())
                    + " <" + MoveParser.PROMOTION_CHOICES + ">.";
        }
        if (!promotes && move.getPromotionPiece() != null) {
            return "Only a pawn reaching the last rank can promote.";
        }
        return null;
    }
}
