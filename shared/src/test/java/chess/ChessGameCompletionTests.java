package chess;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChessGameCompletionTests {

    @Test
    public void endedGameRejectsMovesWithoutChangingBoardOrTurn()
            throws InvalidMoveException {
        ChessGame game = new ChessGame();
        Assertions.assertFalse(game.isGameOver());
        game.makeMove(new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null));
        Assertions.assertFalse(game.isGameOver());
        ChessBoard boardBeforeEnd = new ChessBoard(game.getBoard());
        ChessGame.TeamColor turnBeforeEnd = game.getTeamTurn();
        ChessMove nextMove = new ChessMove(
                new ChessPosition(7, 5), new ChessPosition(5, 5), null);

        game.endGame();
        game.endGame();

        Assertions.assertTrue(game.isGameOver());
        Assertions.assertTrue(game.validMoves(nextMove.getStartPosition()).contains(nextMove));
        InvalidMoveException exception = Assertions.assertThrows(
                InvalidMoveException.class, () -> game.makeMove(nextMove));
        Assertions.assertEquals("The game is over", exception.getMessage());
        Assertions.assertEquals(boardBeforeEnd, game.getBoard());
        Assertions.assertEquals(turnBeforeEnd, game.getTeamTurn());
        Assertions.assertTrue(game.isGameOver());
    }

    @Test
    public void equalityDistinguishesCompletedGamesAndPreservesHashContract() {
        ChessGame activeGame = new ChessGame();
        ChessGame firstCompletedGame = new ChessGame();
        ChessGame secondCompletedGame = new ChessGame();
        firstCompletedGame.endGame();
        secondCompletedGame.endGame();

        Assertions.assertNotEquals(activeGame, firstCompletedGame);
        Assertions.assertNotEquals(firstCompletedGame, activeGame);
        Assertions.assertEquals(firstCompletedGame, secondCompletedGame);
        Assertions.assertEquals(firstCompletedGame.hashCode(), secondCompletedGame.hashCode());
    }
}
