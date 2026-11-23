package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import dataaccess.MemoryGameDAO;
import model.GameData;
import org.junit.jupiter.api.BeforeEach;

/**
 * Starts each test with the white player, black player, and an observer connected to the test game.
 */
abstract class ConnectedGameTestSupport extends WebSocketEndpointTestSupport {

    protected ControlledGameDAO controlledGames;
    protected SocketClient white;
    protected SocketClient black;
    protected SocketClient observer;

    @Override
    protected MemoryGameDAO createGameDAO() {
        controlledGames = new ControlledGameDAO();
        return controlledGames;
    }

    @BeforeEach
    public void connectParticipants() throws Exception {
        white = openSocket();
        black = openSocket();
        observer = openSocket();
        connect(white, "white-token", gameID);
        connect(black, "black-token", gameID);
        assertNotice(white, "black", "BLACK");
        connect(observer, "observer-token", gameID);
        assertNotice(white, "observer", "observer");
        assertNotice(black, "observer", "observer");
    }

    protected static ChessMove move(int startRow, int startColumn, int endRow, int endColumn) {
        return new ChessMove(new ChessPosition(startRow, startColumn),
                new ChessPosition(endRow, endColumn), null);
    }

    protected SocketClient player(ChessGame.TeamColor color) {
        return color == ChessGame.TeamColor.WHITE ? white : black;
    }

    protected String username(ChessGame.TeamColor color) {
        return color == ChessGame.TeamColor.WHITE ? "white" : "black";
    }

    protected GameData snapshot() throws Exception {
        GameData stored = gameDAO.getGame(gameID);
        return new GameData(stored.gameID(), stored.whiteUsername(), stored.blackUsername(),
                stored.gameName(), new ChessGame(stored.game()));
    }

    protected void assertAllQuiet() throws Exception {
        white.assertNoMessage();
        black.assertNoMessage();
        observer.assertNoMessage();
    }
}
