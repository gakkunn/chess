package chess;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChessGameCopyTests {

    @Test
    public void copiedGameCanContinueWithoutChangingTheOriginal() throws InvalidMoveException {
        ChessGame original = new ChessGame();
        original.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));

        ChessGame copy = new ChessGame(original);

        Assertions.assertEquals(original, copy);
        Assertions.assertNotSame(original.getBoard(), copy.getBoard());
        copy.makeMove(new ChessMove(new ChessPosition(7, 5), new ChessPosition(5, 5), null));
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, original.getTeamTurn());
        Assertions.assertEquals(ChessGame.TeamColor.WHITE, copy.getTeamTurn());
        Assertions.assertNotNull(original.getBoard().getPiece(new ChessPosition(7, 5)));
        Assertions.assertNull(copy.getBoard().getPiece(new ChessPosition(7, 5)));
        Assertions.assertNull(original.getBoard().getPiece(new ChessPosition(5, 5)));
    }

    @Test
    public void copiedGamePreservesCompletionAndRejectsMoves() {
        ChessGame original = new ChessGame();
        original.endGame();

        ChessGame copy = new ChessGame(original);

        Assertions.assertTrue(copy.isGameOver());
        Assertions.assertEquals(original, copy);
        Assertions.assertThrows(InvalidMoveException.class,
                () -> copy.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null)));
        Assertions.assertEquals(original, copy);
    }
}
