package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import dataaccess.sql.SQLAuthDAO;
import dataaccess.sql.SQLGameDAO;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import testutil.GameplayPositions;
import websocket.commands.MakeMoveCommand;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

public class WebSocketCompletionPersistenceTests extends SQLWebSocketTestSupport {

    @Test
    public void checkmateRemainsCompletedAfterTheServerRestarts() throws Exception {
        assertCompletionSurvivesRestart(GameplayPositions.beforeCheckmate(ChessGame.TeamColor.WHITE),
                GameplayPositions.matingMove(ChessGame.TeamColor.WHITE));
    }

    @Test
    public void stalemateRemainsCompletedAfterTheServerRestarts() throws Exception {
        assertCompletionSurvivesRestart(GameplayPositions.beforeStalemate(ChessGame.TeamColor.WHITE),
                GameplayPositions.stalematingMove(ChessGame.TeamColor.WHITE));
    }

    private void assertCompletionSurvivesRestart(ChessGame position, ChessMove finishingMove)
            throws Exception {
        gameDAO.updateGame(new GameData(gameID, "white", "black", "Persistent Game", position));
        SocketClient firstClient = openSocket();
        connect(firstClient, "white-token", gameID);
        ChessGame expected = new ChessGame(position);
        expected.makeMove(finishingMove);
        expected.endGame();

        firstClient.send(new MakeMoveCommand("white-token", gameID, finishingMove));

        Assertions.assertEquals(expected,
                Assertions.assertInstanceOf(LoadGameMessage.class, firstClient.receive()).getGame());
        NotificationMessage status = Assertions.assertInstanceOf(NotificationMessage.class, firstClient.receive());
        Assertions.assertTrue(status.getMessage().contains("black"));
        Assertions.assertEquals(expected, new SQLGameDAO().getGame(gameID).game());
        firstClient.assertNoMessage();
        server.stop();
        authDAO = new SQLAuthDAO();
        gameDAO = new SQLGameDAO();
        startServer(gameDAO);

        SocketClient black = openSocket();
        Assertions.assertEquals(expected, connect(black, "black-token", gameID).getGame());
        black.send(new MakeMoveCommand("black-token", gameID,
                new ChessMove(new ChessPosition(8, 1), new ChessPosition(7, 1), null)));
        Assertions.assertTrue(assertError(black).getErrorMessage().contains("The game is over"));
        SocketClient white = openSocket();
        Assertions.assertEquals(expected, connect(white, "white-token", gameID).getGame());
        assertNotice(black, "white", "WHITE");
        white.send(new MakeMoveCommand("white-token", gameID, finishingMove));
        Assertions.assertTrue(assertError(white).getErrorMessage().contains("The game is over"));
        white.assertNoMessage();
        black.assertNoMessage();
        Assertions.assertEquals(expected, new SQLGameDAO().getGame(gameID).game());
    }
}
