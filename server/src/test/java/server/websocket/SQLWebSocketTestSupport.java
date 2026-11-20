package server.websocket;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.sql.SQLAuthDAO;
import dataaccess.sql.SQLDatabaseInitializer;
import dataaccess.sql.SQLGameDAO;
import dataaccess.sql.SQLUserDAO;
import model.UserData;
import server.Server;

import java.util.List;

abstract class SQLWebSocketTestSupport extends WebSocketEndpointTestSupport {

    @Override
    protected AuthDAO createAuthDAO() throws DataAccessException {
        SQLDatabaseInitializer.initialize();
        new SQLAuthDAO().clear();
        new SQLGameDAO().clear();
        SQLUserDAO users = new SQLUserDAO();
        users.clear();
        for (String username : List.of("white", "black", "observer")) {
            users.createUser(new UserData(username, "password", username + "@example.com"));
        }
        return new SQLAuthDAO();
    }

    @Override
    protected GameDAO createGameDAO() {
        return new SQLGameDAO();
    }

    @Override
    protected Server createServer(GameDAO games) {
        return new Server();
    }

}
