package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import dataaccess.sql.SQLAuthDAO;
import dataaccess.sql.SQLGameDAO;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import websocket.commands.MakeMoveCommand;

import java.util.List;

public class WebSocketResignPersistenceTests extends SQLWebSocketTestSupport {

    @ParameterizedTest
    @EnumSource(ChessGame.TeamColor.class)
    public void resignationPersistsAcrossRestartWithoutRemovingPlayers(ChessGame.TeamColor resigningTeam)
            throws Exception {
        ChessMove move = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        ChessGame position = new ChessGame();
        position.makeMove(move);
        gameDAO.updateGame(new GameData(gameID, "white", "black", "Persistent Match", position));
        String username = resigningTeam == ChessGame.TeamColor.WHITE ? "white" : "black";
        SocketClient resigningPlayer = openSocket();
        SocketClient observer = openSocket();
        connect(resigningPlayer, username + "-token", gameID);
        connect(observer, "observer-token", gameID);
        assertNotice(resigningPlayer, "observer", "observer");

        resign(resigningPlayer, username + "-token", gameID);

        assertNotice(resigningPlayer, username, "resigned. The game is over.");
        assertNotice(observer, username, "resigned. The game is over.");
        position.endGame();
        GameData expected = new GameData(gameID, "white", "black", "Persistent Match", position);
        Assertions.assertEquals(expected, new SQLGameDAO().getGame(gameID));
        resigningPlayer.assertNoMessage();
        observer.assertNoMessage();
        server.stop();
        authDAO = new SQLAuthDAO();
        gameDAO = new SQLGameDAO();
        startServer(gameDAO);

        SocketClient white = openSocket();
        SocketClient black = openSocket();
        SocketClient newObserver = openSocket();
        Assertions.assertEquals(position, connect(white, "white-token", gameID).getGame());
        Assertions.assertEquals(position, connect(black, "black-token", gameID).getGame());
        assertNotice(white, "black", "BLACK");
        Assertions.assertEquals(position, connect(newObserver, "observer-token", gameID).getGame());
        assertNotice(white, "observer", "observer");
        assertNotice(black, "observer", "observer");

        white.send(new MakeMoveCommand("white-token", gameID, move));
        Assertions.assertEquals("Error: The game is over", assertError(white).getErrorMessage());
        black.send(new MakeMoveCommand("black-token", gameID,
                new ChessMove(new ChessPosition(7, 5), new ChessPosition(5, 5), null)));
        Assertions.assertEquals("Error: The game is over", assertError(black).getErrorMessage());
        resign(white, "white-token", gameID);
        Assertions.assertEquals("Error: The game is over", assertError(white).getErrorMessage());
        resign(black, "black-token", gameID);
        Assertions.assertEquals("Error: The game is over", assertError(black).getErrorMessage());
        for (SocketClient client : List.of(white, black, newObserver)) {
            client.assertNoMessage();
        }
        Assertions.assertNotNull(authDAO.getAuth(username + "-token"));
        Assertions.assertEquals(expected, new SQLGameDAO().getGame(gameID));
    }
}
