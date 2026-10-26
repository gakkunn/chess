package dataaccess.sql;

import chess.ChessGame;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dataaccess.DataAccessException;

public final class ChessGameJsonSerializer {

    private final Gson serializer = new Gson();

    public String serialize(ChessGame game) throws DataAccessException {
        requireCompleteGame(game);
        return serializer.toJson(game);
    }

    public ChessGame deserialize(String gameJson) throws DataAccessException {
        if (gameJson == null || gameJson.isBlank()) {
            throw new DataAccessException("Chess game JSON is required");
        }

        try {
            JsonElement jsonElement = JsonParser.parseString(gameJson);
            requireCompleteJsonStructure(jsonElement);

            ChessGame game = serializer.fromJson(jsonElement, ChessGame.class);
            requireCompleteGame(game);
            return game;
        } catch (JsonParseException exception) {
            throw new DataAccessException("Unable to deserialize the chess game", exception);
        }
    }

    private void requireCompleteJsonStructure(JsonElement jsonElement)
            throws DataAccessException {
        if (!jsonElement.isJsonObject()) {
            throw new DataAccessException("Chess game JSON must contain an object");
        }

        JsonObject gameObject = jsonElement.getAsJsonObject();
        JsonElement boardElement = gameObject.get("board");
        boolean hasTurn = gameObject.has("teamTurn")
                && !gameObject.get("teamTurn").isJsonNull();
        boolean hasBoard = boardElement != null
                && boardElement.isJsonObject()
                && boardElement.getAsJsonObject().has("squares");

        if (!hasTurn || !hasBoard) {
            throw new DataAccessException("Chess game JSON is incomplete");
        }
    }

    private void requireCompleteGame(ChessGame game) throws DataAccessException {
        if (game == null || game.getTeamTurn() == null || game.getBoard() == null) {
            throw new DataAccessException("A complete chess game is required");
        }
    }
}
