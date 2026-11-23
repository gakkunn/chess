package client;

import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.Locale;

final class MoveParser {

    static final String PROMOTION_CHOICES = "QUEEN|ROOK|BISHOP|KNIGHT";
    static final String USAGE = "Usage: move <from> <to> [" + PROMOTION_CHOICES + "]";

    private MoveParser() {
    }

    static ChessMove parse(String[] tokens) {
        if (tokens.length != 3 && tokens.length != 4) {
            throw new IllegalArgumentException(USAGE);
        }
        ChessPosition from = parsePosition(tokens[1]);
        ChessPosition to = parsePosition(tokens[2]);
        ChessPiece.PieceType promotion = tokens.length == 4 ? parsePromotion(tokens[3]) : null;
        return new ChessMove(from, to, promotion);
    }

    static ChessPosition parsePosition(String square) {
        String normalized = square.toLowerCase(Locale.ROOT);
        if (normalized.length() != 2 || normalized.charAt(0) < 'a' || normalized.charAt(0) > 'h'
                || normalized.charAt(1) < '1' || normalized.charAt(1) > '8') {
            throw new IllegalArgumentException("Use board squares a1 through h8, such as e2 and e4.");
        }
        return new ChessPosition(normalized.charAt(1) - '0', normalized.charAt(0) - 'a' + 1);
    }

    static String formatPosition(ChessPosition position) {
        return "" + (char) ('a' + position.getColumn() - 1) + position.getRow();
    }

    private static ChessPiece.PieceType parsePromotion(String piece) {
        return switch (piece.toUpperCase(Locale.ROOT)) {
            case "QUEEN" -> ChessPiece.PieceType.QUEEN;
            case "ROOK" -> ChessPiece.PieceType.ROOK;
            case "BISHOP" -> ChessPiece.PieceType.BISHOP;
            case "KNIGHT" -> ChessPiece.PieceType.KNIGHT;
            default -> throw new IllegalArgumentException("Promote to QUEEN, ROOK, BISHOP, or KNIGHT.");
        };
    }
}
