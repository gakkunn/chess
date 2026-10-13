package server.handler;

import com.google.gson.Gson;
import dataaccess.DataAccessException;
import io.javalin.http.Context;
import service.UserService;
import service.exception.AlreadyTakenException;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.LoginRequest;
import service.request.LogoutRequest;
import service.request.RegisterRequest;
import service.result.LoginResult;
import service.result.LogoutResult;
import service.result.RegisterResult;

/**
 * Translates user HTTP requests into service operations and JSON responses.
 */
public class UserHandler {

    private final UserService userService;
    private final Gson serializer;

    public UserHandler(UserService userService, Gson serializer) {
        this.userService = userService;
        this.serializer = serializer;
    }

    public void handleRegister(Context context)
            throws BadRequestException, AlreadyTakenException, DataAccessException {
        RegisterRequest request = serializer.fromJson(context.body(), RegisterRequest.class);
        RegisterResult result = userService.register(request);

        context.status(200);
        context.contentType("application/json");
        context.result(serializer.toJson(result));
    }

    public void handleLogin(Context context)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        LoginRequest request = serializer.fromJson(context.body(), LoginRequest.class);
        LoginResult result = userService.login(request);

        context.status(200);
        context.contentType("application/json");
        context.result(serializer.toJson(result));
    }

    public void handleLogout(Context context)
            throws UnauthorizedException, DataAccessException {
        LogoutRequest request = new LogoutRequest(context.header("authorization"));
        LogoutResult result = userService.logout(request);

        context.status(200);
        context.contentType("application/json");
        context.result(serializer.toJson(result));
    }
}
