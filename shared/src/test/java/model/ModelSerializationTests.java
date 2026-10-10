package model;

import chess.ChessGame;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModelSerializationTests {

    private final Gson serializer = new Gson();

    @Test
    public void userDataRoundTripPreservesAllFields() {
        UserData originalUser = new UserData(
                "reader",
                "clear-password",
                "reader@example.com");

        String json = serializer.toJson(originalUser);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        UserData restoredUser = serializer.fromJson(json, UserData.class);

        Assertions.assertEquals("reader", jsonObject.get("username").getAsString());
        Assertions.assertEquals("clear-password", jsonObject.get("password").getAsString());
        Assertions.assertEquals("reader@example.com", jsonObject.get("email").getAsString());
        Assertions.assertEquals(originalUser, restoredUser);
    }

    @Test
    public void authDataRoundTripPreservesAllFields() {
        AuthData originalAuth = new AuthData("token-123", "reader");

        String json = serializer.toJson(originalAuth);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        AuthData restoredAuth = serializer.fromJson(json, AuthData.class);

        Assertions.assertEquals("token-123", jsonObject.get("authToken").getAsString());
        Assertions.assertEquals("reader", jsonObject.get("username").getAsString());
        Assertions.assertEquals(originalAuth, restoredAuth);
    }

    @Test
    public void gameDataRoundTripPreservesAllFields() {
        GameData originalGame = new GameData(
                27,
                "white-player",
                "black-player",
                "Evening Match",
                new ChessGame());

        String json = serializer.toJson(originalGame);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        GameData restoredGame = serializer.fromJson(json, GameData.class);

        Assertions.assertEquals(27, jsonObject.get("gameID").getAsInt());
        Assertions.assertEquals("white-player", jsonObject.get("whiteUsername").getAsString());
        Assertions.assertEquals("black-player", jsonObject.get("blackUsername").getAsString());
        Assertions.assertEquals("Evening Match", jsonObject.get("gameName").getAsString());
        Assertions.assertTrue(jsonObject.has("game"));
        Assertions.assertEquals(originalGame, restoredGame);
    }

    @Test
    public void gameDataAllowsOpenPlayerSlots() {
        GameData waitingGame = new GameData(
                28,
                null,
                null,
                "Waiting for Players",
                new ChessGame());

        Assertions.assertNull(waitingGame.whiteUsername());
        Assertions.assertNull(waitingGame.blackUsername());
        Assertions.assertNotNull(waitingGame.game());
    }
}
