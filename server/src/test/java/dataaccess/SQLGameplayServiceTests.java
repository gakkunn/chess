package dataaccess;

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
import org.junit.jupiter.api.Test;
import service.GameplayService;
import service.request.MakeMoveRequest;

public class SQLGameplayServiceTests extends SQLDAOTestSupportTests {

    @Test
    public void movesPersistAndNewServiceInstancesCanContinueTheStoredGame() throws Exception {
        SQLUserDAO users = new SQLUserDAO();
        SQLAuthDAO auth = new SQLAuthDAO();
        SQLGameDAO games = new SQLGameDAO();
        users.createUser(new UserData("white", "password", "white@example.com"));
        users.createUser(new UserData("black", "password", "black@example.com"));
        auth.createAuth(new AuthData("white-token", "white"));
        auth.createAuth(new AuthData("black-token", "black"));
        int gameID = games.createGame(new GameData(
                0, "white", "black", "Persistent Match", new ChessGame()));
        ChessMove whiteMove = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        ChessMove blackMove = new ChessMove(new ChessPosition(7, 5), new ChessPosition(5, 5), null);
        ChessGame expected = new ChessGame();
        expected.makeMove(whiteMove);

        new GameplayService(auth, games).makeMove(new MakeMoveRequest("white-token", gameID, whiteMove));

        GameData stored = new SQLGameDAO().getGame(gameID);
        Assertions.assertEquals(expected, stored.game());
        Assertions.assertEquals("white", stored.whiteUsername());
        Assertions.assertEquals("black", stored.blackUsername());
        Assertions.assertEquals("Persistent Match", stored.gameName());
        expected.makeMove(blackMove);

        new GameplayService(new SQLAuthDAO(), new SQLGameDAO())
                .makeMove(new MakeMoveRequest("black-token", gameID, blackMove));

        Assertions.assertEquals(expected, new SQLGameDAO().getGame(gameID).game());
    }
}
