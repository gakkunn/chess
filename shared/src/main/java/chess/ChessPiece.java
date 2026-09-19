package chess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single chess piece
 * <p>
 * Note: You can add to this class, but you may not alter
 * signature of the existing methods.
 */
public class ChessPiece {

    private final ChessGame.TeamColor teamColor;
    private final PieceType pieceType;

    public ChessPiece(ChessGame.TeamColor pieceColor, ChessPiece.PieceType type) {
        this.teamColor = pieceColor;
        this.pieceType = type;
    }

    /**
     * The various different chess piece options
     */
    public enum PieceType {
        KING,
        QUEEN,
        BISHOP,
        KNIGHT,
        ROOK,
        PAWN
    }

    /**
     * @return Which team this chess piece belongs to
     */
    public ChessGame.TeamColor getTeamColor() {
        return teamColor;
    }

    /**
     * @return which type of chess piece this piece is
     */
    public PieceType getPieceType() {
        return pieceType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChessPiece that)) {
            return false;
        }
        return teamColor == that.teamColor && pieceType == that.pieceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamColor, pieceType);
    }

    /**
     * Calculates all the positions a chess piece can move to
     * Does not take into account moves that are illegal due to leaving the king in
     * danger
     *
     * @return Collection of valid moves
     */
    public Collection<ChessMove> pieceMoves(ChessBoard board, ChessPosition myPosition) {
        if (pieceType == PieceType.KNIGHT) {
            List<ChessMove> moves = new ArrayList<>();
            int[][] offsets = {
                    {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                    {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
            };

            for (int[] offset : offsets) {
                int row = myPosition.getRow() + offset[0];
                int column = myPosition.getColumn() + offset[1];
                if (row < 1 || row > 8 || column < 1 || column > 8) {
                    continue;
                }

                ChessPosition destination = new ChessPosition(row, column);
                ChessPiece pieceAtDestination = board.getPiece(destination);
                if (pieceAtDestination == null || pieceAtDestination.getTeamColor() != teamColor) {
                    moves.add(new ChessMove(myPosition, destination, null));
                }
            }
            return moves;
        }

        throw new RuntimeException("Not implemented");
    }
}
