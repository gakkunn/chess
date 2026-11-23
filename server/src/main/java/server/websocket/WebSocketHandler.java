package server.websocket;

import chess.ChessMove;
import chess.ChessPosition;
import chess.InvalidMoveException;
import com.google.gson.JsonParseException;
import dataaccess.DataAccessException;
import io.javalin.websocket.WsMessageContext;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.GameLockManager;
import service.GameplayService;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.ConnectGameRequest;
import service.request.LeaveGameRequest;
import service.request.MakeMoveRequest;
import service.request.ResignGameRequest;
import service.result.ConnectGameResult;
import service.result.LeaveGameResult;
import service.result.MakeMoveResult;
import service.result.ResignGameResult;
import websocket.WebSocketMessageCodec;
import websocket.commands.MakeMoveCommand;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

/**
 * Handles gameplay commands and sends the resulting messages to connected clients.
 */
public final class WebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketHandler.class);

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
            if (command.getGameID() == null || command.getGameID() <= 0) {
                throw new BadRequestException();
            }

            synchronized (gameLocks.forGame(command.getGameID())) {
                switch (command.getCommandType()) {
                    case CONNECT -> connect(context.session, command);
                    case MAKE_MOVE -> makeMove(context.session, (MakeMoveCommand) command);
                    case LEAVE -> leave(context.session, command);
                    case RESIGN -> resign(context.session, command);
                }
            }
        } catch (JsonParseException | BadRequestException exception) {
            connections.send(context.session, new ErrorMessage("bad request"));
        } catch (UnauthorizedException | InvalidMoveException exception) {
            connections.send(context.session, new ErrorMessage(exception.getMessage()));
        } catch (DataAccessException | RuntimeException exception) {
            LOGGER.error("Unable to process a WebSocket command.", exception);
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

    private void makeMove(Session session, MakeMoveCommand command)
            throws BadRequestException, UnauthorizedException, DataAccessException, InvalidMoveException {
        requireConnection(session, command);

        MakeMoveResult result = gameplayService.makeMove(
                new MakeMoveRequest(command.getAuthToken(), command.getGameID(), command.getMove()));
        connections.broadcast(command.getGameID(), new LoadGameMessage(result.game().game()), null);
        connections.broadcast(command.getGameID(),
                new NotificationMessage(describeMove(result.username(), command.getMove())), session);
        if (result.statusMessage() != null) {
            connections.broadcast(command.getGameID(), new NotificationMessage(result.statusMessage()), null);
        }
    }

    private void leave(Session session, UserGameCommand command)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        requireConnection(session, command);
        LeaveGameResult result = gameplayService.leave(
                new LeaveGameRequest(command.getAuthToken(), command.getGameID()));
        connections.remove(session);
        connections.broadcast(command.getGameID(),
                new NotificationMessage(result.username() + " left the game."), session);
    }

    private void resign(Session session, UserGameCommand command)
            throws BadRequestException, UnauthorizedException, DataAccessException, InvalidMoveException {
        requireConnection(session, command);
        ResignGameResult result = gameplayService.resign(
                new ResignGameRequest(command.getAuthToken(), command.getGameID()));
        connections.broadcast(command.getGameID(),
                new NotificationMessage(result.username() + " resigned. The game is over.", true), null);
    }

    private void requireConnection(Session session, UserGameCommand command) throws BadRequestException {
        if (!connections.contains(session, command.getAuthToken(), command.getGameID())) {
            throw new BadRequestException();
        }
    }

    private String describeMove(String username, ChessMove move) {
        String description = username + " moved " + squareName(move.getStartPosition())
                + " to " + squareName(move.getEndPosition());
        if (move.getPromotionPiece() != null) {
            description += " and promoted to " + move.getPromotionPiece();
        }
        return description + ".";
    }

    private String squareName(ChessPosition position) {
        return "" + (char) ('a' + position.getColumn() - 1) + position.getRow();
    }
}
