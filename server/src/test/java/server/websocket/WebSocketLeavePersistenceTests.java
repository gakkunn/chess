package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import dataaccess.sql.SQLAuthDAO;
import dataaccess.sql.SQLGameDAO;
import dataaccess.sql.SQLUserDAO;
import model.AuthData;
import model.GameData;
import model.UserData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import websocket.commands.MakeMoveCommand;
import websocket.messages.LoadGameMessage;

public class WebSocketLeavePersistenceTests extends SQLWebSocketTestSupport {

    @ParameterizedTest
    @EnumSource(ChessGame.TeamColor.class)
    public void vacatedSlotsPersistAcrossRestartAndReplacementPlayersCanContinue(ChessGame.TeamColor color)
            throws Exception {
        ChessGame position = new ChessGame();
        position.makeMove(move(2, 5, 4, 5));
        if (color == ChessGame.TeamColor.WHITE) {
            position.makeMove(move(7, 5, 5, 5));
        }
        gameDAO.updateGame(new GameData(gameID, "white", "black", "Persistent Match", position));
        String departingName = color == ChessGame.TeamColor.WHITE ? "white" : "black";
        SocketClient departing = openSocket();
        SocketClient observer = openSocket();
        connect(departing, departingName + "-token", gameID);
        connect(observer, "observer-token", gameID);
        assertNotice(departing, "observer", "observer");

        leave(departing, departingName + "-token", gameID);

        assertNotice(observer, departingName, "left the game.");
        departing.assertNoMessage();
        GameData expected = new GameData(gameID,
                color == ChessGame.TeamColor.WHITE ? null : "white",
                color == ChessGame.TeamColor.BLACK ? null : "black", "Persistent Match", position);
        Assertions.assertEquals(expected, new SQLGameDAO().getGame(gameID));
        server.stop();
        authDAO = new SQLAuthDAO();
        gameDAO = new SQLGameDAO();
        startServer(gameDAO);
        Assertions.assertEquals(expected, new SQLGameDAO().getGame(gameID));
        Assertions.assertNotNull(authDAO.getAuth(departingName + "-token"));

        new SQLUserDAO().createUser(new UserData("replacement", "password", "replacement@example.com"));
        authDAO.createAuth(new AuthData("replacement-token", "replacement"));
        joinGame("replacement-token", color, gameID);
        SocketClient replacement = openSocket();
        Assertions.assertEquals(position, connect(replacement, "replacement-token", gameID).getGame());
        SocketClient newObserver = openSocket();
        connect(newObserver, "observer-token", gameID);
        assertNotice(replacement, "observer", "observer");
        ChessMove knightMove = color == ChessGame.TeamColor.WHITE ? move(1, 7, 3, 6) : move(8, 7, 6, 6);
        ChessGame afterMove = new ChessGame(position);
        afterMove.makeMove(knightMove);

        replacement.send(new MakeMoveCommand("replacement-token", gameID, knightMove));

        Assertions.assertEquals(afterMove,
                Assertions.assertInstanceOf(LoadGameMessage.class, replacement.receive()).getGame());
        Assertions.assertEquals(afterMove,
                Assertions.assertInstanceOf(LoadGameMessage.class, newObserver.receive()).getGame());
        assertNotice(newObserver, "replacement", "moved");
        GameData stored = new SQLGameDAO().getGame(gameID);
        Assertions.assertEquals(afterMove, stored.game());
        Assertions.assertEquals("replacement",
                color == ChessGame.TeamColor.WHITE ? stored.whiteUsername() : stored.blackUsername());
        replacement.assertNoMessage();
    }

    private ChessMove move(int fromRow, int fromColumn, int toRow, int toColumn) {
        return new ChessMove(new ChessPosition(fromRow, fromColumn), new ChessPosition(toRow, toColumn), null);
    }
}
