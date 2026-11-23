package client;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;

/**
 * Board positions one move before check, checkmate, or stalemate, with ranks mirrored for black.
 * <p>
 * The client module keeps its own copy because Maven does not put the server's test classes
 * on the client's test classpath.
 */
final class GameplayPositions {

    private GameplayPositions() {
    }

    static ChessGame beforeCheck(ChessGame.TeamColor movingTeam) {
        ChessBoard board = new ChessBoard();
        board.addPiece(position(movingTeam, 1, 5), new ChessPiece(movingTeam, ChessPiece.PieceType.KING));
        board.addPiece(position(movingTeam, 8, 5), new ChessPiece(opponent(movingTeam), ChessPiece.PieceType.KING));
        board.addPiece(position(movingTeam, 2, 1), new ChessPiece(movingTeam, ChessPiece.PieceType.ROOK));
        return game(movingTeam, board);
    }

    static ChessMove checkingMove(ChessGame.TeamColor movingTeam) {
        return move(movingTeam, 2, 1, 2, 5);
    }

    static ChessMove escapeCheckMove(ChessGame.TeamColor checkingTeam) {
        return move(checkingTeam, 8, 5, 8, 4);
    }

    static ChessGame beforeCheckmate(ChessGame.TeamColor movingTeam) {
        return kingAndQueenGame(movingTeam, 6, 2);
    }

    static ChessMove matingMove(ChessGame.TeamColor movingTeam) {
        return move(movingTeam, 6, 2, 7, 2);
    }

    static ChessGame beforeStalemate(ChessGame.TeamColor movingTeam) {
        return kingAndQueenGame(movingTeam, 7, 3);
    }

    static ChessMove stalematingMove(ChessGame.TeamColor movingTeam) {
        return move(movingTeam, 7, 3, 6, 2);
    }

    private static ChessGame kingAndQueenGame(ChessGame.TeamColor movingTeam, int queenRow, int queenColumn) {
        ChessBoard board = new ChessBoard();
        board.addPiece(position(movingTeam, 6, 3), new ChessPiece(movingTeam, ChessPiece.PieceType.KING));
        board.addPiece(position(movingTeam, 8, 1), new ChessPiece(opponent(movingTeam), ChessPiece.PieceType.KING));
        board.addPiece(position(movingTeam, queenRow, queenColumn),
                new ChessPiece(movingTeam, ChessPiece.PieceType.QUEEN));
        return game(movingTeam, board);
    }

    private static ChessGame game(ChessGame.TeamColor movingTeam, ChessBoard board) {
        ChessGame game = new ChessGame();
        game.setBoard(board);
        game.setTeamTurn(movingTeam);
        return game;
    }

    private static ChessMove move(
            ChessGame.TeamColor movingTeam, int fromRow, int fromColumn, int toRow, int toColumn) {
        return new ChessMove(position(movingTeam, fromRow, fromColumn), position(movingTeam, toRow, toColumn), null);
    }

    private static ChessPosition position(ChessGame.TeamColor movingTeam, int whiteRow, int column) {
        return new ChessPosition(movingTeam == ChessGame.TeamColor.WHITE ? whiteRow : 9 - whiteRow, column);
    }

    private static ChessGame.TeamColor opponent(ChessGame.TeamColor team) {
        return team == ChessGame.TeamColor.WHITE ? ChessGame.TeamColor.BLACK : ChessGame.TeamColor.WHITE;
    }
}
