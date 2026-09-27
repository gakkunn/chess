package chess;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A class that can manage a chess game, making moves on a board
 * <p>
 * Note: You can add to this class, but you may not alter
 * signature of the existing methods.
 */
public class ChessGame {

    private TeamColor teamTurn;
    private ChessBoard board;

    public ChessGame() {
        board = new ChessBoard();
        board.resetBoard();
        teamTurn = TeamColor.WHITE;
    }

    /**
     * @return Which team's turn it is
     */
    public TeamColor getTeamTurn() {
        return teamTurn;
    }

    /**
     * Sets which teams turn it is
     *
     * @param team the team whose turn it is
     */
    public void setTeamTurn(TeamColor team) {
        teamTurn = team;
    }

    /**
     * Enum identifying the 2 possible teams in a chess game
     */
    public enum TeamColor {
        WHITE,
        BLACK
    }

    /**
     * Gets all valid moves for a piece at the given location
     *
     * @param startPosition the piece to get valid moves for
     * @return Set of valid moves for requested piece, or null if no piece at
     * startPosition
     */
    public Collection<ChessMove> validMoves(ChessPosition startPosition) {
        ChessPiece movingPiece = board.getPiece(startPosition);

        if (movingPiece == null) {
            return null;
        }

        Collection<ChessMove> pieceMoves = movingPiece.pieceMoves(board, startPosition);
        Collection<ChessMove> validMoves = new ArrayList<>();

        for (ChessMove move : pieceMoves) {
            ChessBoard boardAfterMove = new ChessBoard(board);
            movePieceOnBoard(boardAfterMove, move);

            boolean kingWouldBeInCheck =
                    isInCheck(boardAfterMove, movingPiece.getTeamColor());

            if (!kingWouldBeInCheck) {
                validMoves.add(move);
            }
        }

        return validMoves;
    }

    /**
     * Moves a piece on the supplied board without changing the game turn.
     * This is used to test a possible move on a copied board.
     */
    private void movePieceOnBoard(ChessBoard boardToChange, ChessMove move) {
        ChessPiece movingPiece = boardToChange.getPiece(move.getStartPosition());

        boardToChange.addPiece(move.getStartPosition(), null);
        boardToChange.addPiece(move.getEndPosition(), movingPiece);
    }

    /**
     * Makes a move in the chess game
     *
     * @param move chess move to perform
     * @throws InvalidMoveException if move is invalid
     */
    public void makeMove(ChessMove move) throws InvalidMoveException {
        if (move == null
                || move.getStartPosition() == null
                || move.getEndPosition() == null) {
            throw new InvalidMoveException("A move must have a start and end position");
        }

        ChessPiece movingPiece = board.getPiece(move.getStartPosition());

        if (movingPiece == null) {
            throw new InvalidMoveException("There is no piece at the start position");
        }

        if (movingPiece.getTeamColor() != teamTurn) {
            throw new InvalidMoveException("The selected piece cannot move during this turn");
        }

        Collection<ChessMove> legalMoves = validMoves(move.getStartPosition());

        if (legalMoves == null || !legalMoves.contains(move)) {
            throw new InvalidMoveException("The requested move is not legal");
        }

        movePieceOnBoard(board, move);
        promotePawnIfNeeded(move, movingPiece);

        if (teamTurn == TeamColor.WHITE) {
            teamTurn = TeamColor.BLACK;
        } else {
            teamTurn = TeamColor.WHITE;
        }
    }

    /**
     * Replaces a pawn with the selected promotion piece after it reaches the
     * opposite end of the board. The move has already been validated before
     * this method is called.
     */
    private void promotePawnIfNeeded(ChessMove move, ChessPiece movingPiece) {
        ChessPiece.PieceType promotionType = move.getPromotionPiece();

        if (promotionType == null) {
            return;
        }

        ChessPiece promotedPiece = new ChessPiece(
                movingPiece.getTeamColor(),
                promotionType);

        board.addPiece(move.getEndPosition(), promotedPiece);
    }

    /**
     * Determines if the given team is in check
     *
     * @param teamColor which team to check for check
     * @return True if the specified team is in check
     */
    public boolean isInCheck(TeamColor teamColor) {
        return isInCheck(board, teamColor);
    }

    /**
     * Checks whether a team's king is being attacked on a particular board.
     * This overload will also be useful when a move is tested on a copied board.
     */
    private boolean isInCheck(ChessBoard boardToCheck, TeamColor teamColor) {
        ChessPosition kingPosition = findKingPosition(boardToCheck, teamColor);

        if (kingPosition == null) {
            return false;
        }

        TeamColor opponentColor;
        if (teamColor == TeamColor.WHITE) {
            opponentColor = TeamColor.BLACK;
        } else {
            opponentColor = TeamColor.WHITE;
        }

        for (int row = 1; row <= 8; row++) {
            for (int column = 1; column <= 8; column++) {
                ChessPosition opponentPosition = new ChessPosition(row, column);
                ChessPiece opponentPiece = boardToCheck.getPiece(opponentPosition);

                if (opponentPiece == null || opponentPiece.getTeamColor() != opponentColor) {
                    continue;
                }

                Collection<ChessMove> opponentMoves =
                        opponentPiece.pieceMoves(boardToCheck, opponentPosition);

                for (ChessMove opponentMove : opponentMoves) {
                    if (opponentMove.getEndPosition().equals(kingPosition)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Finds the king belonging to the requested team.
     *
     * @return the king's position, or null when that king is not on the board
     */
    private ChessPosition findKingPosition(ChessBoard boardToSearch, TeamColor teamColor) {
        for (int row = 1; row <= 8; row++) {
            for (int column = 1; column <= 8; column++) {
                ChessPosition position = new ChessPosition(row, column);
                ChessPiece piece = boardToSearch.getPiece(position);

                if (piece == null) {
                    continue;
                }

                boolean belongsToRequestedTeam = piece.getTeamColor() == teamColor;
                boolean isKing = piece.getPieceType() == ChessPiece.PieceType.KING;

                if (belongsToRequestedTeam && isKing) {
                    return position;
                }
            }
        }

        return null;
    }

    /**
     * Determines if the given team is in checkmate
     *
     * @param teamColor which team to check for checkmate
     * @return True if the specified team is in checkmate
     */
    public boolean isInCheckmate(TeamColor teamColor) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Determines if the given team is in stalemate, which here is defined as having
     * no valid moves while not in check.
     *
     * @param teamColor which team to check for stalemate
     * @return True if the specified team is in stalemate, otherwise false
     */
    public boolean isInStalemate(TeamColor teamColor) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Sets this game's chessboard to a given board
     *
     * @param board the new board to use
     */
    public void setBoard(ChessBoard board) {
        this.board = board;
    }

    /**
     * Gets the current chessboard
     *
     * @return the chessboard
     */
    public ChessBoard getBoard() {
        return board;
    }
}
