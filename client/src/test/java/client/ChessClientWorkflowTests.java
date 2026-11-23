package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import client.GameplayFacadeTestSupport.TestGameplayClient;
import dataaccess.sql.SQLGameDAO;
import model.GameData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.Server;

import java.util.ArrayList;
import java.util.List;

public class ChessClientWorkflowTests {

    private final List<TestGameplayClient> clients = new ArrayList<>();
    private Server server;
    private ServerFacade admin;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = new Server();
        port = server.run(0);
        admin = new ServerFacade(port);
        admin.clear();
    }

    @AfterEach
    void closeConnections() {
        for (TestGameplayClient client : clients) {
            client.close();
        }
        admin.close();
        server.stop();
    }

    @Test
    void registrationThroughCheckmateAndRestartPreservesTheCompletedGame() throws Exception {
        TestGameplayClient whitePlayer = register("white", ChessGame.TeamColor.WHITE);
        TestGameplayClient blackPlayer = register("black", ChessGame.TeamColor.BLACK);
        TestGameplayClient watcher = register("observer", null);
        Assertions.assertEquals("Created game 'Complete Match'.", whitePlayer.evaluate("create Complete Match"));
        Assertions.assertTrue(whitePlayer.evaluate("list").contains("1. Complete Match"));
        whitePlayer.enter(1);
        blackPlayer.enter(1);
        watcher.enter(1);
        whitePlayer.awaitContaining("observer connected");
        blackPlayer.awaitContaining("observer connected");
        TestGameplayClient[] participants = {whitePlayer, blackPlayer, watcher};
        ChessGame expected = new ChessGame();
        Assertions.assertTrue(watcher.evaluate("highlight f2").startsWith("Selected f2"));
        Assertions.assertTrue(watcher.evaluate("move f2 f3").contains("Observers cannot"));
        String[] commands = {"move f2 f3", "move e7 e5", "move g2 g4", "move d8 h4"};
        ChessMove[] moves = {move(2, 6, 3, 6), move(7, 5, 5, 5), move(2, 7, 4, 7), move(8, 4, 4, 8)};
        for (int index = 0; index < moves.length; index++) {
            TestGameplayClient mover = index % 2 == 0 ? whitePlayer : blackPlayer;
            Assertions.assertEquals("Move submitted.", mover.evaluate(commands[index]));
            expected.makeMove(moves[index]);
            if (index == moves.length - 1) {
                expected.endGame();
            }
            for (TestGameplayClient participant : participants) {
                participant.awaitBoard(expected);
            }
        }
        for (TestGameplayClient participant : participants) {
            participant.awaitContaining("white is in checkmate");
        }
        int gameID = new SQLGameDAO().listGames().getFirst().gameID();
        Assertions.assertEquals(expected, new SQLGameDAO().getGame(gameID).game());
        Assertions.assertTrue(whitePlayer.evaluate("move e2 e4").contains("game is over"));
        Assertions.assertTrue(blackPlayer.evaluate("resign").contains("already over"));
        whitePlayer.evaluate("leave");
        watcher.awaitContaining("white left the game");
        blackPlayer.evaluate("leave");
        watcher.awaitContaining("black left the game");
        GameData completed = new SQLGameDAO().getGame(gameID);
        Assertions.assertNull(completed.whiteUsername());
        Assertions.assertNull(completed.blackUsername());
        Assertions.assertEquals(expected, completed.game());
        Assertions.assertEquals("Signed out.", whitePlayer.evaluate("logout"));
        Assertions.assertEquals("Signed in as white.", whitePlayer.evaluate("login white password"));

        server.stop();
        watcher.awaitContaining("reconnect");
        server = new Server();
        Assertions.assertEquals(port, server.run(port));
        Assertions.assertTrue(watcher.evaluate("reconnect").contains("Connecting"));
        Assertions.assertTrue(watcher.await(text -> text.startsWith("Observing")).endsWith(watcher.render(expected)));
        Assertions.assertEquals(completed, new SQLGameDAO().getGame(gameID));
        Assertions.assertEquals("Left 'Complete Match'.", watcher.evaluate("leave"));
        Assertions.assertEquals("Created game 'Next Match'.", whitePlayer.evaluate("create Next Match"));
        Assertions.assertEquals(2, new SQLGameDAO().listGames().size());
        for (TestGameplayClient participant : participants) {
            Assertions.assertEquals("Signed out.", participant.evaluate("logout"));
            Assertions.assertEquals(ClientState.SIGNED_OUT, participant.client.getState());
            Assertions.assertEquals("Goodbye.", participant.evaluate("quit"));
            Assertions.assertEquals(ClientState.QUIT, participant.client.getState());
        }
    }

    private TestGameplayClient register(String username, ChessGame.TeamColor color) {
        TestGameplayClient client = new TestGameplayClient(new ServerFacade(port), color);
        clients.add(client);
        Assertions.assertEquals(ClientState.SIGNED_OUT, client.client.getState());
        Assertions.assertEquals("Signed in as " + username + ".",
                client.evaluate("register " + username + " password " + username + "@example.com"));
        return client;
    }

    private ChessMove move(int fromRow, int fromColumn, int toRow, int toColumn) {
        return new ChessMove(new ChessPosition(fromRow, fromColumn), new ChessPosition(toRow, toColumn), null);
    }
}
