package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.UserDAO;
import dataaccess.sql.SQLAuthDAO;
import dataaccess.sql.SQLDatabaseInitializer;
import dataaccess.sql.SQLGameDAO;
import dataaccess.sql.SQLUserDAO;
import io.javalin.Javalin;
import server.handler.ClearHandler;
import server.handler.GameHandler;
import server.handler.HttpErrorHandler;
import server.handler.UserHandler;
import service.ClearService;
import service.GameService;
import service.UserService;

/**
 * Configures and runs the HTTP API for the chess server.
 */
public class Server {

    private final Javalin javalin;

    public Server() {
        this(new SQLUserDAO(), new SQLAuthDAO(), new SQLGameDAO());
        initializeDatabase();
    }

    public Server(UserDAO userDAO, AuthDAO authDAO, GameDAO gameDAO) {
        javalin = Javalin.create(config -> config.staticFiles.add("web"));

        Gson serializer = new GsonBuilder().serializeNulls().create();
        ClearService clearService = new ClearService(userDAO, authDAO, gameDAO);
        UserService userService = new UserService(userDAO, authDAO);
        GameService gameService = new GameService(authDAO, gameDAO);
        ClearHandler clearHandler = new ClearHandler(clearService, serializer);
        UserHandler userHandler = new UserHandler(userService, serializer);
        GameHandler gameHandler = new GameHandler(gameService, serializer);

        javalin.delete("/db", clearHandler::handleClear);
        javalin.post("/user", userHandler::handleRegister);
        javalin.post("/session", userHandler::handleLogin);
        javalin.delete("/session", userHandler::handleLogout);
        javalin.post("/game", gameHandler::handleCreateGame);
        javalin.get("/game", gameHandler::handleListGames);
        javalin.put("/game", gameHandler::handleJoinGame);
        HttpErrorHandler.register(javalin, serializer);
    }

    private void initializeDatabase() {
        try {
            SQLDatabaseInitializer.initialize();
        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Unable to initialize the chess database",
                    exception);
        }
    }

    /**
     * Starts the server on the requested port.
     *
     * @param desiredPort the port that Javalin should use
     * @return the port used by the running server
     */
    public int run(int desiredPort) {
        javalin.start(desiredPort);
        return javalin.port();
    }

    /**
     * Stops the server and releases its network resources.
     */
    public void stop() {
        javalin.stop();
    }
}
