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
import server.websocket.ConnectionManager;
import server.websocket.WebSocketHandler;
import service.ClearService;
import service.GameLockManager;
import service.GameService;
import service.GameplayService;
import service.UserService;

/**
 * Configures the chess HTTP API and gameplay WebSocket endpoint.
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
        GameLockManager gameLocks = new GameLockManager();
        ConnectionManager connections = new ConnectionManager();
        ClearService clearService = new ClearService(userDAO, authDAO, gameDAO);
        UserService userService = new UserService(userDAO, authDAO);
        GameService gameService = new GameService(authDAO, gameDAO, gameLocks);
        GameplayService gameplayService = new GameplayService(authDAO, gameDAO);
        ClearHandler clearHandler = new ClearHandler(clearService, serializer);
        UserHandler userHandler = new UserHandler(userService, serializer);
        GameHandler gameHandler = new GameHandler(gameService, serializer);
        WebSocketHandler webSocketHandler = new WebSocketHandler(
                gameplayService, connections, gameLocks);

        javalin.delete("/db", context -> {
            clearHandler.handleClear(context);
            connections.clear();
        });
        javalin.post("/user", userHandler::handleRegister);
        javalin.post("/session", userHandler::handleLogin);
        javalin.delete("/session", userHandler::handleLogout);
        javalin.post("/game", gameHandler::handleCreateGame);
        javalin.get("/game", gameHandler::handleListGames);
        javalin.put("/game", gameHandler::handleJoinGame);
        javalin.ws("/ws", ws -> {
            ws.onConnect(context -> context.enableAutomaticPings());
            ws.onMessage(webSocketHandler::handleMessage);
            ws.onClose(context -> connections.remove(context.session));
            ws.onError(context -> connections.remove(context.session));
        });
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
