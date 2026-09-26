package chess;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

public class ChessGameValidMovesTests {

    @Test
    public void emptyPositionHasNoMoves() {
        ChessGame game = new ChessGame();
        ChessPosition emptyPosition = new ChessPosition(4, 4);

        Assertions.assertNull(game.validMoves(emptyPosition),
                "An empty position should return null instead of a collection of moves");
    }

    @Test
    public void calculatingMovesDoesNotChangeGameState() {
        ChessGame game = new ChessGame();
        game.setTeamTurn(ChessGame.TeamColor.BLACK);

        ChessBoard boardBeforeCalculation = new ChessBoard(game.getBoard());
        ChessPosition whitePawnPosition = new ChessPosition(2, 5);

        Collection<ChessMove> moves = game.validMoves(whitePawnPosition);

        Assertions.assertEquals(2, moves.size(),
                "The white pawn should have both starting moves even during black's turn");
        Assertions.assertEquals(boardBeforeCalculation, game.getBoard(),
                "Calculating valid moves should not change the game board");
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, game.getTeamTurn(),
                "Calculating valid moves should not change the game turn");
    }
}
