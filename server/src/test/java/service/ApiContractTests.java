package service;

import chess.ChessGame;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.exception.AlreadyTakenException;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.ClearRequest;
import service.request.CreateGameRequest;
import service.request.JoinGameRequest;
import service.request.ListGamesRequest;
import service.request.LoginRequest;
import service.request.LogoutRequest;
import service.request.RegisterRequest;
import service.result.ClearResult;
import service.result.CreateGameResult;
import service.result.ErrorResult;
import service.result.GameSummary;
import service.result.JoinGameResult;
import service.result.ListGamesResult;
import service.result.LoginResult;
import service.result.LogoutResult;
import service.result.RegisterResult;

import java.util.List;

public class ApiContractTests {

    private final Gson serializer = new GsonBuilder().serializeNulls().create();

    @Test
    public void registerRequestUsesExpectedJsonProperties() {
        RegisterRequest originalRequest = new RegisterRequest(
                "new-user",
                "secret-password",
                "new-user@example.com");

        String json = serializer.toJson(originalRequest);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        RegisterRequest restoredRequest = serializer.fromJson(json, RegisterRequest.class);

        Assertions.assertEquals("new-user", jsonObject.get("username").getAsString());
        Assertions.assertEquals("secret-password", jsonObject.get("password").getAsString());
        Assertions.assertEquals("new-user@example.com", jsonObject.get("email").getAsString());
        Assertions.assertEquals(originalRequest, restoredRequest);
    }

    @Test
    public void authenticationResultsUseExpectedJsonProperties() {
        RegisterResult registerResult = new RegisterResult("new-user", "register-token");
        LoginResult loginResult = new LoginResult("existing-user", "login-token");

        JsonObject registerJson = serializer.toJsonTree(registerResult).getAsJsonObject();
        JsonObject loginJson = serializer.toJsonTree(loginResult).getAsJsonObject();

        Assertions.assertEquals("new-user", registerJson.get("username").getAsString());
        Assertions.assertEquals("register-token", registerJson.get("authToken").getAsString());
        Assertions.assertEquals("existing-user", loginJson.get("username").getAsString());
        Assertions.assertEquals("login-token", loginJson.get("authToken").getAsString());
    }

    @Test
    public void authenticatedRequestsCarryTheAuthorizationToken() {
        LogoutRequest logoutRequest = new LogoutRequest("logout-token");
        ListGamesRequest listRequest = new ListGamesRequest("list-token");
        CreateGameRequest createRequest = new CreateGameRequest("create-token", "Morning Match");
        JoinGameRequest joinRequest = new JoinGameRequest(
                "join-token",
                ChessGame.TeamColor.BLACK,
                14);

        Assertions.assertEquals("logout-token", logoutRequest.authToken());
        Assertions.assertEquals("list-token", listRequest.authToken());
        Assertions.assertEquals("create-token", createRequest.authToken());
        Assertions.assertEquals("join-token", joinRequest.authToken());
    }

    @Test
    public void joinRequestDeserializesAValidTeamColor() {
        String json = "{\"playerColor\":\"WHITE\",\"gameID\":12}";

        JoinGameRequest request = serializer.fromJson(json, JoinGameRequest.class);

        Assertions.assertNull(request.authToken());
        Assertions.assertEquals(ChessGame.TeamColor.WHITE, request.playerColor());
        Assertions.assertEquals(12, request.gameID());
    }

    @Test
    public void joinRequestTreatsAnUnknownTeamColorAsMissing() {
        String json = "{\"playerColor\":\"GREEN\",\"gameID\":12}";

        JoinGameRequest request = serializer.fromJson(json, JoinGameRequest.class);

        Assertions.assertNull(request.playerColor());
        Assertions.assertEquals(12, request.gameID());
    }

    @Test
    public void gameResultsUseExpectedJsonProperties() {
        CreateGameResult createResult = new CreateGameResult(31);
        GameSummary gameSummary = new GameSummary(31, null, "black-player", "Evening Match");
        ListGamesResult listResult = new ListGamesResult(List.of(gameSummary));

        JsonObject createJson = serializer.toJsonTree(createResult).getAsJsonObject();
        JsonArray gamesJson = serializer.toJsonTree(listResult)
                .getAsJsonObject()
                .getAsJsonArray("games");
        JsonObject gameJson = gamesJson.get(0).getAsJsonObject();

        Assertions.assertEquals(31, createJson.get("gameID").getAsInt());
        Assertions.assertEquals(31, gameJson.get("gameID").getAsInt());
        Assertions.assertTrue(gameJson.get("whiteUsername").isJsonNull());
        Assertions.assertEquals("black-player", gameJson.get("blackUsername").getAsString());
        Assertions.assertEquals("Evening Match", gameJson.get("gameName").getAsString());
        Assertions.assertFalse(gameJson.has("game"));
    }

    @Test
    public void emptyRequestsAndResultsSerializeAsEmptyObjects() {
        Assertions.assertEquals("{}", serializer.toJson(new ClearRequest()));
        Assertions.assertEquals("{}", serializer.toJson(new ClearResult()));
        Assertions.assertEquals("{}", serializer.toJson(new LogoutResult()));
        Assertions.assertEquals("{}", serializer.toJson(new JoinGameResult()));
    }

    @Test
    public void errorResultUsesTheMessageProperty() {
        ErrorResult errorResult = new ErrorResult("Error: example failure");

        JsonObject errorJson = serializer.toJsonTree(errorResult).getAsJsonObject();

        Assertions.assertEquals("Error: example failure", errorJson.get("message").getAsString());
    }

    @Test
    public void serviceExceptionsUseTheRequiredMessages() {
        BadRequestException badRequest = new BadRequestException();
        UnauthorizedException unauthorized = new UnauthorizedException();
        AlreadyTakenException alreadyTaken = new AlreadyTakenException();

        Assertions.assertEquals("Error: bad request", badRequest.getMessage());
        Assertions.assertEquals("Error: unauthorized", unauthorized.getMessage());
        Assertions.assertEquals("Error: already taken", alreadyTaken.getMessage());
    }

    @Test
    public void loginRequestRoundTripPreservesCredentials() {
        LoginRequest originalRequest = new LoginRequest("existing-user", "secret-password");

        String json = serializer.toJson(originalRequest);
        LoginRequest restoredRequest = serializer.fromJson(json, LoginRequest.class);

        Assertions.assertEquals(originalRequest, restoredRequest);
    }
}
