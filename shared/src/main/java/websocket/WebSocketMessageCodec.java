package websocket;

import chess.ChessGame;
import chess.ChessPiece;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import websocket.commands.MakeMoveCommand;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.util.Objects;

/**
 * Converts WebSocket JSON to the command or message indicated by its type field.
 */
public final class WebSocketMessageCodec {

    private static final int BOARD_SIZE = 8;

    private final Gson serializer = new Gson();

    public String serialize(UserGameCommand command) {
        return serializer.toJson(Objects.requireNonNull(command));
    }

    public String serialize(ServerMessage message) {
        return serializer.toJson(Objects.requireNonNull(message));
    }

    public UserGameCommand deserializeCommand(String json) {
        JsonObject object = parseObject(json);
        UserGameCommand.CommandType type = readType(
                object, "commandType", UserGameCommand.CommandType.class);

        if (type == UserGameCommand.CommandType.MAKE_MOVE) {
            JsonObject move = requireObject(object, "move");
            requireObject(move, "startPosition");
            requireObject(move, "endPosition");
            if (move.has("promotionPiece") && !move.get("promotionPiece").isJsonNull()) {
                readType(move, "promotionPiece", ChessPiece.PieceType.class);
            }
            return serializer.fromJson(object, MakeMoveCommand.class);
        }

        return serializer.fromJson(object, UserGameCommand.class);
    }

    public ServerMessage deserializeMessage(String json) {
        JsonObject object = parseObject(json);
        ServerMessage.ServerMessageType type = readType(
                object, "serverMessageType", ServerMessage.ServerMessageType.class);

        return switch (type) {
            case LOAD_GAME -> deserializeLoadGame(object);
            case ERROR -> {
                requireString(object, "errorMessage");
                yield serializer.fromJson(object, ErrorMessage.class);
            }
            case NOTIFICATION -> {
                requireString(object, "message");
                requireOptionalBoolean(object, "gameOver");
                yield serializer.fromJson(object, NotificationMessage.class);
            }
        };
    }

    private LoadGameMessage deserializeLoadGame(JsonObject object) {
        JsonObject game = requireObject(object, "game");
        readType(game, "teamTurn", ChessGame.TeamColor.class);
        requireBoard(requireObject(game, "board"));
        requireOptionalBoolean(game, "gameOver");

        return serializer.fromJson(object, LoadGameMessage.class);
    }

    private void requireBoard(JsonObject board) {
        JsonArray rows = requireBoardArray(board.get("squares"));
        for (JsonElement row : rows) {
            for (JsonElement square : requireBoardArray(row)) {
                requirePieceOrEmpty(square);
            }
        }
    }

    private JsonArray requireBoardArray(JsonElement element) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != BOARD_SIZE) {
            throw new JsonParseException("The game must contain an 8 by 8 board.");
        }

        return element.getAsJsonArray();
    }

    private void requirePieceOrEmpty(JsonElement square) {
        if (square.isJsonNull()) {
            return;
        }
        if (!square.isJsonObject()) {
            throw new JsonParseException("A board square must contain a piece or null.");
        }

        readType(square.getAsJsonObject(), "teamColor", ChessGame.TeamColor.class);
        readType(square.getAsJsonObject(), "pieceType", ChessPiece.PieceType.class);
    }

    private JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            throw new JsonParseException("A WebSocket JSON object is required.");
        }

        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new JsonParseException("A WebSocket message must be a JSON object.");
        }

        return element.getAsJsonObject();
    }

    private JsonObject requireObject(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new JsonParseException("An object is required for " + field + ".");
        }

        return value.getAsJsonObject();
    }

    private String requireString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new JsonParseException("A string is required for " + field + ".");
        }

        return value.getAsString();
    }

    private void requireOptionalBoolean(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value != null && !value.isJsonNull()
                && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())) {
            throw new JsonParseException("A boolean is required for " + field + ".");
        }
    }

    private <T extends Enum<T>> T readType(
            JsonObject object, String field, Class<T> type) {
        String value = requireString(object, field);

        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Unknown " + field + ".", exception);
        }
    }
}
