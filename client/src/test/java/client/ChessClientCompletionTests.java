package client;

import chess.ChessGame;
import chess.ChessMove;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ChessClientCompletionTests extends GameplayFacadeTestSupport {

    @ParameterizedTest
    @CsvSource({"WHITE, check", "BLACK, check", "WHITE, checkmate", "BLACK, checkmate",
            "WHITE, stalemate", "BLACK, stalemate"})
    void allClientsConvergeOnCheckAndTerminalGameStates(ChessGame.TeamColor movingTeam, String status) throws Exception {
        ChessGame position = switch (status) {
            case "check" -> GameplayPositions.beforeCheck(movingTeam);
            case "checkmate" -> GameplayPositions.beforeCheckmate(movingTeam);
            default -> GameplayPositions.beforeStalemate(movingTeam);
        };
        ChessMove move = switch (status) {
            case "check" -> GameplayPositions.checkingMove(movingTeam);
            case "checkmate" -> GameplayPositions.matingMove(movingTeam);
            default -> GameplayPositions.stalematingMove(movingTeam);
        };
        games.updateGame(new GameData(gameID, "white", "black", "Live Game", position));
        TestGameplayClient whitePlayer = connect("white", ChessGame.TeamColor.WHITE);
        TestGameplayClient blackPlayer = connect("black", ChessGame.TeamColor.BLACK);
        TestGameplayClient watcher = connect("observer", null);
        TestGameplayClient mover = movingTeam == ChessGame.TeamColor.WHITE ? whitePlayer : blackPlayer;
        TestGameplayClient opponent = movingTeam == ChessGame.TeamColor.WHITE ? blackPlayer : whitePlayer;
        TestGameplayClient[] clients = {whitePlayer, blackPlayer, watcher};
        whitePlayer.awaitContaining("observer connected");
        blackPlayer.awaitContaining("observer connected");
        ChessGame expected = new ChessGame(position);
        expected.makeMove(move);
        boolean terminal = !status.equals("check");
        if (terminal) {
            expected.endGame();
        }

        Assertions.assertEquals("Move submitted.", mover.evaluate(TestGameplayClient.command(move)));
        String opponentName = movingTeam == ChessGame.TeamColor.WHITE ? "black" : "white";
        String notice = switch (status) {
            case "check" -> opponentName + " is in check.";
            case "checkmate" -> opponentName + " is in checkmate. The game is over.";
            default -> "Stalemate: " + opponentName + " has no legal moves. The game is a draw.";
        };
        for (TestGameplayClient client : clients) {
            client.awaitBoard(expected);
            Assertions.assertEquals(notice, client.awaitContaining(notice));
            Assertions.assertEquals(ClientState.IN_GAME, client.client.getState());
            Assertions.assertTrue(client.facade.isConnected());
        }
        Assertions.assertEquals(expected, games.getGame(gameID).game());
        if (terminal) {
            for (TestGameplayClient player : new TestGameplayClient[]{whitePlayer, blackPlayer}) {
                Assertions.assertTrue(player.evaluate("move a1 a2").contains("game is over"));
                Assertions.assertTrue(player.evaluate("resign").contains("already over"));
            }
        } else {
            ChessMove escape = GameplayPositions.escapeCheckMove(movingTeam);
            Assertions.assertEquals("Move submitted.", opponent.evaluate(TestGameplayClient.command(escape)));
            expected.makeMove(escape);
            for (TestGameplayClient client : clients) {
                client.awaitBoard(expected);
            }
            Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
        }
    }

    private TestGameplayClient connect(String username, ChessGame.TeamColor color) throws Exception {
        TestGameplayClient client = new TestGameplayClient(facade(port), color);
        client.evaluate("login " + username + " password");
        client.enter(1);
        return client;
    }
}
