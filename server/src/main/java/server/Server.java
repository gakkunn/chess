package server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dataaccess.AuthDAO;
import dataaccess.GameDAO;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import dataaccess.UserDAO;
import io.javalin.Javalin;
import server.handler.ClearHandler;
import server.handler.HttpErrorHandler;
import server.handler.UserHandler;
import service.ClearService;
import service.UserService;

public class Server {

    private final Javalin javalin;

    public Server() {
        this(new MemoryUserDAO(), new MemoryAuthDAO(), new MemoryGameDAO());
    }

    public Server(UserDAO userDAO, AuthDAO authDAO, GameDAO gameDAO) {
        javalin = Javalin.create(config -> config.staticFiles.add("web"));

        Gson serializer = new GsonBuilder().serializeNulls().create();
        ClearService clearService = new ClearService(userDAO, authDAO, gameDAO);
        UserService userService = new UserService(userDAO, authDAO);
        ClearHandler clearHandler = new ClearHandler(clearService, serializer);
        UserHandler userHandler = new UserHandler(userService, serializer);

        javalin.delete("/db", clearHandler::handleClear);
        javalin.post("/user", userHandler::handleRegister);
        HttpErrorHandler.register(javalin, serializer);
    }

    public int run(int desiredPort) {
        javalin.start(desiredPort);
        return javalin.port();
    }

    public void stop() {
        javalin.stop();
    }
}
