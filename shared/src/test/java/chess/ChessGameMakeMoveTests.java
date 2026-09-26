package chess;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChessGameMakeMoveTests {

    @Test
    public void rejectedMoveDoesNotChangeBoardOrTurn() {
        ChessGame game = new ChessGame();
        ChessBoard boardBeforeMove = new ChessBoard(game.getBoard());
        ChessGame.TeamColor turnBeforeMove = game.getTeamTurn();

        ChessMove illegalMove = new ChessMove(
                new ChessPosition(2, 1),
                new ChessPosition(5, 1),
                null);

        Assertions.assertThrows(InvalidMoveException.class,
                () -> game.makeMove(illegalMove),
                "Moving a pawn three squares should be rejected");

        Assertions.assertEquals(boardBeforeMove, game.getBoard(),
                "A rejected move should not change the board");
        Assertions.assertEquals(turnBeforeMove, game.getTeamTurn(),
                "A rejected move should not change the current turn");
    }
}
