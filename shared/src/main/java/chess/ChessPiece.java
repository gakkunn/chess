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

    private static final int[][] KNIGHT_OFFSETS = {
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
    };
    private static final int[][] ROOK_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };
    private static final int[][] BISHOP_DIRECTIONS = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[][] QUEEN_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

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
        return switch (pieceType) {
            case KING -> calculateKingMoves(board, myPosition);
            case KNIGHT -> calculateKnightMoves(board, myPosition);
            case ROOK -> calculateSlidingMoves(board, myPosition, ROOK_DIRECTIONS);
            case BISHOP -> calculateSlidingMoves(board, myPosition, BISHOP_DIRECTIONS);
            case QUEEN -> calculateSlidingMoves(board, myPosition, QUEEN_DIRECTIONS);
            case PAWN -> calculatePawnMoves(board, myPosition);
        };
    }

    private List<ChessMove> calculateKingMoves(ChessBoard board, ChessPosition start) {
        List<ChessMove> moves = new ArrayList<>();

        for (int rowChange = -1; rowChange <= 1; rowChange++) {
            for (int columnChange = -1; columnChange <= 1; columnChange++) {
                boolean remainsInPlace = rowChange == 0 && columnChange == 0;
                if (remainsInPlace) {
                    continue;
                }

                addMoveIfAvailable(board, start, moves, rowChange, columnChange);
            }
        }

        return moves;
    }

    private List<ChessMove> calculateKnightMoves(ChessBoard board, ChessPosition start) {
        List<ChessMove> moves = new ArrayList<>();

        for (int[] offset : KNIGHT_OFFSETS) {
            addMoveIfAvailable(board, start, moves, offset[0], offset[1]);
        }

        return moves;
    }

    private void addMoveIfAvailable(
            ChessBoard board,
            ChessPosition start,
            List<ChessMove> moves,
            int rowChange,
            int columnChange) {
        int row = start.getRow() + rowChange;
        int column = start.getColumn() + columnChange;

        if (!isOnBoard(row, column)) {
            return;
        }

        ChessPosition destination = new ChessPosition(row, column);
        ChessPiece pieceAtDestination = board.getPiece(destination);
        boolean destinationIsAvailable = pieceAtDestination == null
                || pieceAtDestination.getTeamColor() != teamColor;

        if (destinationIsAvailable) {
            moves.add(new ChessMove(start, destination, null));
        }
    }

    private List<ChessMove> calculateSlidingMoves(
            ChessBoard board,
            ChessPosition start,
            int[][] directions) {
        List<ChessMove> moves = new ArrayList<>();

        for (int[] direction : directions) {
            addMovesInDirection(
                    board,
                    start,
                    moves,
                    direction[0],
                    direction[1]);
        }

        return moves;
    }

    private List<ChessMove> calculatePawnMoves(ChessBoard board, ChessPosition start) {
        List<ChessMove> moves = new ArrayList<>();
        int direction = teamColor == ChessGame.TeamColor.WHITE ? 1 : -1;
        int startingRow = teamColor == ChessGame.TeamColor.WHITE ? 2 : 7;
        int nextRow = start.getRow() + direction;

        addForwardPawnMoves(board, start, moves, direction, startingRow, nextRow);
        addPawnCaptures(board, start, moves, nextRow);

        return moves;
    }

    private void addForwardPawnMoves(
            ChessBoard board,
            ChessPosition start,
            List<ChessMove> moves,
            int direction,
            int startingRow,
            int nextRow) {
        ChessPosition oneSquareForward = new ChessPosition(nextRow, start.getColumn());

        if (board.getPiece(oneSquareForward) != null) {
            return;
        }

        addPawnMove(moves, start, oneSquareForward);

        if (start.getRow() == startingRow) {
            ChessPosition twoSquaresForward =
                    new ChessPosition(nextRow + direction, start.getColumn());
            if (board.getPiece(twoSquaresForward) == null) {
                moves.add(new ChessMove(start, twoSquaresForward, null));
            }
        }
    }

    private void addPawnCaptures(
            ChessBoard board,
            ChessPosition start,
            List<ChessMove> moves,
            int nextRow) {
        for (int columnChange : new int[]{-1, 1}) {
            int column = start.getColumn() + columnChange;
            if (!isOnBoard(nextRow, column)) {
                continue;
            }

            ChessPosition destination = new ChessPosition(nextRow, column);
            ChessPiece pieceAtDestination = board.getPiece(destination);
            boolean canCapture = pieceAtDestination != null
                    && pieceAtDestination.getTeamColor() != teamColor;

            if (canCapture) {
                addPawnMove(moves, start, destination);
            }
        }
    }

    private boolean isOnBoard(int row, int column) {
        return row >= 1 && row <= 8 && column >= 1 && column <= 8;
    }

    private void addPawnMove(List<ChessMove> moves, ChessPosition start, ChessPosition destination) {
        boolean reachesLastRow = destination.getRow() == 1 || destination.getRow() == 8;

        if (reachesLastRow) {
            moves.add(new ChessMove(start, destination, PieceType.QUEEN));
            moves.add(new ChessMove(start, destination, PieceType.BISHOP));
            moves.add(new ChessMove(start, destination, PieceType.ROOK));
            moves.add(new ChessMove(start, destination, PieceType.KNIGHT));
        } else {
            moves.add(new ChessMove(start, destination, null));
        }
    }

    private void addMovesInDirection(ChessBoard board, ChessPosition start,
                                     List<ChessMove> moves, int rowChange, int columnChange) {
        int row = start.getRow() + rowChange;
        int column = start.getColumn() + columnChange;

        while (isOnBoard(row, column)) {
            ChessPosition destination = new ChessPosition(row, column);
            ChessPiece pieceAtDestination = board.getPiece(destination);

            if (pieceAtDestination == null) {
                moves.add(new ChessMove(start, destination, null));
            } else {
                if (pieceAtDestination.getTeamColor() != teamColor) {
                    moves.add(new ChessMove(start, destination, null));
                }
                break;
            }

            row += rowChange;
            column += columnChange;
        }
    }
}
