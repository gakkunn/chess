package dataaccess;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;
import chess.InvalidMoveException;
import dataaccess.sql.ChessGameJsonSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChessGameJsonSerializerTests {

    private final ChessGameJsonSerializer serializer = new ChessGameJsonSerializer();

    @Test
    public void initialGameRoundTripPreservesState() throws DataAccessException {
        ChessGame originalGame = new ChessGame();

        ChessGame restoredGame = roundTrip(originalGame);

        Assertions.assertEquals(originalGame, restoredGame);
        Assertions.assertNotSame(originalGame, restoredGame);
        Assertions.assertNotSame(originalGame.getBoard(), restoredGame.getBoard());
    }

    @Test
    public void restoredGameCanContinueAfterAMove()
            throws DataAccessException, InvalidMoveException {
        ChessGame originalGame = new ChessGame();
        originalGame.makeMove(move(2, 5, 4, 5));

        ChessGame restoredGame = roundTrip(originalGame);
        restoredGame.makeMove(move(7, 5, 5, 5));

        Assertions.assertEquals(ChessGame.TeamColor.WHITE, restoredGame.getTeamTurn());
        Assertions.assertNull(restoredGame.getBoard().getPiece(position(7, 5)));
        Assertions.assertEquals(
                new ChessPiece(
                        ChessGame.TeamColor.BLACK,
                        ChessPiece.PieceType.PAWN),
                restoredGame.getBoard().getPiece(position(5, 5)));
    }

    @Test
    public void customBoardRoundTripPreservesPiecesAndTurn() throws DataAccessException {
        ChessBoard customBoard = new ChessBoard();
        customBoard.addPiece(
                position(1, 5),
                new ChessPiece(
                        ChessGame.TeamColor.WHITE,
                        ChessPiece.PieceType.KING));
        customBoard.addPiece(
                position(8, 5),
                new ChessPiece(
                        ChessGame.TeamColor.BLACK,
                        ChessPiece.PieceType.KING));
        customBoard.addPiece(
                position(4, 4),
                new ChessPiece(
                        ChessGame.TeamColor.BLACK,
                        ChessPiece.PieceType.QUEEN));
        ChessGame originalGame = new ChessGame();
        originalGame.setBoard(customBoard);
        originalGame.setTeamTurn(ChessGame.TeamColor.BLACK);

        ChessGame restoredGame = roundTrip(originalGame);

        Assertions.assertEquals(originalGame, restoredGame);
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, restoredGame.getTeamTurn());
        Assertions.assertEquals(
                customBoard.getPiece(position(4, 4)),
                restoredGame.getBoard().getPiece(position(4, 4)));
    }

    @Test
    public void promotedPieceRoundTripPreservesPromotion()
            throws DataAccessException, InvalidMoveException {
        ChessGame originalGame = createPromotionGame();
        originalGame.makeMove(new ChessMove(
                position(7, 1),
                position(8, 1),
                ChessPiece.PieceType.QUEEN));

        ChessGame restoredGame = roundTrip(originalGame);
        ChessPiece promotedPiece = restoredGame.getBoard().getPiece(position(8, 1));

        Assertions.assertEquals(originalGame, restoredGame);
        Assertions.assertEquals(
                new ChessPiece(
                        ChessGame.TeamColor.WHITE,
                        ChessPiece.PieceType.QUEEN),
                promotedPiece);
        Assertions.assertNull(restoredGame.getBoard().getPiece(position(7, 1)));
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, restoredGame.getTeamTurn());
    }

    @Test
    public void serializeRejectsMissingGame() {
        Assertions.assertThrows(
                DataAccessException.class,
                () -> serializer.serialize(null));
    }

    @Test
    public void deserializeRejectsInvalidGameJson() {
        String[] invalidGameJsonValues = {
                null,
                "",
                "   ",
                "null",
                "{}",
                "{invalid-json"
        };

        for (String invalidGameJson : invalidGameJsonValues) {
            Assertions.assertThrows(
                    DataAccessException.class,
                    () -> serializer.deserialize(invalidGameJson));
        }
    }

    private ChessGame roundTrip(ChessGame game) throws DataAccessException {
        String gameJson = serializer.serialize(game);
        return serializer.deserialize(gameJson);
    }

    private ChessGame createPromotionGame() {
        ChessBoard board = new ChessBoard();
        board.addPiece(
                position(1, 5),
                new ChessPiece(
                        ChessGame.TeamColor.WHITE,
                        ChessPiece.PieceType.KING));
        board.addPiece(
                position(8, 5),
                new ChessPiece(
                        ChessGame.TeamColor.BLACK,
                        ChessPiece.PieceType.KING));
        board.addPiece(
                position(7, 1),
                new ChessPiece(
                        ChessGame.TeamColor.WHITE,
                        ChessPiece.PieceType.PAWN));

        ChessGame game = new ChessGame();
        game.setBoard(board);
        game.setTeamTurn(ChessGame.TeamColor.WHITE);
        return game;
    }

    private ChessMove move(int startRow, int startColumn, int endRow, int endColumn) {
        return new ChessMove(
                position(startRow, startColumn),
                position(endRow, endColumn),
                null);
    }

    private ChessPosition position(int row, int column) {
        return new ChessPosition(row, column);
    }
}
