package ui;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessPiece;
import chess.ChessPosition;

import java.util.Objects;

public class BoardRenderer {

    private static final int BOARD_SIZE = 8;
    private static final String LINE_SEPARATOR = System.lineSeparator();

    public String renderWhite(ChessBoard board) {
        Objects.requireNonNull(board, "Chess board cannot be null.");

        StringBuilder output = new StringBuilder();
        appendColumnLabels(output);

        for (int row = BOARD_SIZE; row >= 1; row--) {
            appendRow(output, board, row);
        }

        appendColumnLabels(output);
        return output.toString();
    }

    private void appendColumnLabels(StringBuilder output) {
        output.append(EscapeSequences.SET_BG_COLOR_LIGHT_GREY)
                .append(EscapeSequences.SET_TEXT_COLOR_BLACK)
                .append("   ");

        for (int column = 1; column <= BOARD_SIZE; column++) {
            char label = (char) ('a' + column - 1);
            output.append(' ').append(label).append(' ');
        }

        output.append("   ");
        appendColorReset(output);
        output.append(LINE_SEPARATOR);
    }

    private void appendRow(
            StringBuilder output,
            ChessBoard board,
            int row) {
        appendRowLabel(output, row);

        for (int column = 1; column <= BOARD_SIZE; column++) {
            appendSquare(output, board, row, column);
        }

        appendRowLabel(output, row);
        appendColorReset(output);
        output.append(LINE_SEPARATOR);
    }

    private void appendRowLabel(StringBuilder output, int row) {
        output.append(EscapeSequences.SET_BG_COLOR_LIGHT_GREY)
                .append(EscapeSequences.SET_TEXT_COLOR_BLACK)
                .append(' ')
                .append(row)
                .append(' ');
    }

    private void appendSquare(
            StringBuilder output,
            ChessBoard board,
            int row,
            int column) {
        output.append(squareBackground(row, column));

        ChessPosition position = new ChessPosition(row, column);
        ChessPiece piece = board.getPiece(position);

        if (piece == null) {
            output.append(EscapeSequences.SET_TEXT_COLOR_BLACK)
                    .append(EscapeSequences.EMPTY);
            return;
        }

        output.append(pieceTextColor(piece))
                .append(pieceSymbol(piece));
    }

    private String squareBackground(int row, int column) {
        boolean isLightSquare = (row + column) % 2 == 1;

        if (isLightSquare) {
            return EscapeSequences.SET_BG_COLOR_WHITE;
        }

        return EscapeSequences.SET_BG_COLOR_DARK_GREEN;
    }

    private String pieceTextColor(ChessPiece piece) {
        if (piece.getTeamColor() == ChessGame.TeamColor.WHITE) {
            return EscapeSequences.SET_TEXT_COLOR_RED;
        }

        return EscapeSequences.SET_TEXT_COLOR_BLUE;
    }

    private String pieceSymbol(ChessPiece piece) {
        if (piece.getTeamColor() == ChessGame.TeamColor.WHITE) {
            return whitePieceSymbol(piece.getPieceType());
        }

        return blackPieceSymbol(piece.getPieceType());
    }

    private String whitePieceSymbol(ChessPiece.PieceType pieceType) {
        return switch (pieceType) {
            case KING -> EscapeSequences.WHITE_KING;
            case QUEEN -> EscapeSequences.WHITE_QUEEN;
            case BISHOP -> EscapeSequences.WHITE_BISHOP;
            case KNIGHT -> EscapeSequences.WHITE_KNIGHT;
            case ROOK -> EscapeSequences.WHITE_ROOK;
            case PAWN -> EscapeSequences.WHITE_PAWN;
        };
    }

    private String blackPieceSymbol(ChessPiece.PieceType pieceType) {
        return switch (pieceType) {
            case KING -> EscapeSequences.BLACK_KING;
            case QUEEN -> EscapeSequences.BLACK_QUEEN;
            case BISHOP -> EscapeSequences.BLACK_BISHOP;
            case KNIGHT -> EscapeSequences.BLACK_KNIGHT;
            case ROOK -> EscapeSequences.BLACK_ROOK;
            case PAWN -> EscapeSequences.BLACK_PAWN;
        };
    }

    private void appendColorReset(StringBuilder output) {
        output.append(EscapeSequences.RESET_TEXT_COLOR)
                .append(EscapeSequences.RESET_BG_COLOR);
    }
}
