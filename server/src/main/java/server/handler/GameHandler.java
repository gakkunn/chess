package server.handler;

import com.google.gson.Gson;
import dataaccess.DataAccessException;
import io.javalin.http.Context;
import service.GameService;
import service.exception.AlreadyTakenException;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.CreateGameRequest;
import service.request.JoinGameRequest;
import service.request.ListGamesRequest;
import service.result.CreateGameResult;
import service.result.JoinGameResult;
import service.result.ListGamesResult;

/**
 * Translates game HTTP requests into service operations and JSON responses.
 */
public class GameHandler {

    private final GameService gameService;
    private final Gson serializer;

    public GameHandler(GameService gameService, Gson serializer) {
        this.gameService = gameService;
        this.serializer = serializer;
    }

    public void handleCreateGame(Context context)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        CreateGameRequest requestBody = serializer.fromJson(
                context.body(),
                CreateGameRequest.class);
        String gameName = requestBody == null ? null : requestBody.gameName();
        CreateGameRequest request = new CreateGameRequest(
                context.header("authorization"),
                gameName);

        CreateGameResult result = gameService.createGame(request);

        context.status(200);
        context.contentType("application/json");
        context.result(serializer.toJson(result));
    }

    public void handleListGames(Context context)
            throws UnauthorizedException, DataAccessException {
        ListGamesRequest request = new ListGamesRequest(
                context.header("authorization"));
        ListGamesResult result = gameService.listGames(request);

        context.status(200);
        context.contentType("application/json");
        context.result(serializer.toJson(result));
    }

    public void handleJoinGame(Context context)
            throws BadRequestException, UnauthorizedException,
            AlreadyTakenException, DataAccessException {
        JoinGameRequest requestBody = serializer.fromJson(
                context.body(),
                JoinGameRequest.class);
        JoinGameRequest request = new JoinGameRequest(
                context.header("authorization"),
                requestBody == null ? null : requestBody.playerColor(),
                requestBody == null ? null : requestBody.gameID());

        JoinGameResult result = gameService.joinGame(request);

        context.status(200);
        context.contentType("application/json");
        context.result(serializer.toJson(result));
    }
}
