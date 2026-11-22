package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;

final class MoveValidator {

    private MoveValidator() {
    }

    static String validate(ChessGame game, ChessGame.TeamColor color, ChessMove move, String[] tokens) {
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
            return "Choose a promotion piece: move " + tokens[1] + " " + tokens[2]
                    + " <" + MoveParser.PROMOTION_CHOICES + ">.";
        }
        if (!promotes && move.getPromotionPiece() != null) {
            return "Only a pawn reaching the last rank can promote.";
        }
        return null;
    }
}
