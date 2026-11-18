package server.websocket;

import com.google.gson.JsonParseException;
import dataaccess.DataAccessException;
import io.javalin.websocket.WsMessageContext;
import org.eclipse.jetty.websocket.api.Session;
import service.GameLockManager;
import service.GameplayService;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.ConnectGameRequest;
import service.result.ConnectGameResult;
import websocket.WebSocketMessageCodec;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

public final class WebSocketHandler {

    private final GameplayService gameplayService;
    private final ConnectionManager connections;
    private final GameLockManager gameLocks;
    private final WebSocketMessageCodec codec = new WebSocketMessageCodec();

    public WebSocketHandler(
            GameplayService gameplayService,
            ConnectionManager connections,
            GameLockManager gameLocks) {
        this.gameplayService = gameplayService;
        this.connections = connections;
        this.gameLocks = gameLocks;
    }

    public void handleMessage(WsMessageContext context) {
        try {
            UserGameCommand command = codec.deserializeCommand(context.message());
            if (command.getGameID() == null || command.getGameID() <= 0
                    || command.getCommandType() != UserGameCommand.CommandType.CONNECT) {
                throw new BadRequestException();
            }

            synchronized (gameLocks.forGame(command.getGameID())) {
                connect(context.session, command);
            }
        } catch (JsonParseException | BadRequestException exception) {
            connections.send(context.session, new ErrorMessage("bad request"));
        } catch (UnauthorizedException exception) {
            connections.send(context.session, new ErrorMessage(exception.getMessage()));
        } catch (DataAccessException | RuntimeException exception) {
            connections.send(context.session, new ErrorMessage("internal server error"));
        }
    }

    private void connect(Session session, UserGameCommand command)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        ConnectGameResult result = gameplayService.connect(
                new ConnectGameRequest(command.getAuthToken(), command.getGameID()));
        boolean newConnection = connections.add(session, command.getAuthToken(), command.getGameID());
        if (!newConnection
                && !connections.contains(session, command.getAuthToken(), command.getGameID())) {
            throw new BadRequestException();
        }

        boolean delivered = connections.send(session, new LoadGameMessage(result.game().game()));
        if (newConnection && delivered) {
            String role = result.playerColor() == null
                    ? "an observer"
                    : "a " + result.playerColor() + " player";
            connections.broadcast(command.getGameID(),
                    new NotificationMessage(result.username() + " connected as " + role + "."),
                    session);
        }
    }
}
