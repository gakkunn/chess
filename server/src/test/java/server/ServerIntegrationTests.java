package server;

import chess.ChessGame;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.CreateGameRequest;
import service.request.JoinGameRequest;
import service.request.RegisterRequest;
import service.result.CreateGameResult;
import service.result.GameSummary;
import service.result.ListGamesResult;
import service.result.LoginResult;
import service.result.RegisterResult;
import testutil.UnexpectedFailureUserDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;

public class ServerIntegrationTests extends EndpointTestSupport {

    @Test
    public void completeWorkflowKeepsIndependentSessionsAndClearsApplication()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult owner = registerSuccessfully(
                serverUri,
                "owner",
                "owner@example.com");
        RegisterResult opponent = registerSuccessfully(
                serverUri,
                "opponent",
                "opponent@example.com");
        LoginResult ownerLogin = loginSuccessfully(serverUri, "owner");
        CreateGameResult createdGame = createGameSuccessfully(
                serverUri,
                ownerLogin.authToken(),
                "Championship Match");

        HttpResponse<String> whiteJoinResponse = joinGame(
                serverUri,
                owner.authToken(),
                ChessGame.TeamColor.WHITE,
                createdGame.gameID());
        HttpResponse<String> blackJoinResponse = joinGame(
                serverUri,
                opponent.authToken(),
                ChessGame.TeamColor.BLACK,
                createdGame.gameID());
        assertSuccessfulEmptyResponse(whiteJoinResponse);
        assertSuccessfulEmptyResponse(blackJoinResponse);

        HttpResponse<String> listResponse = requestGames(serverUri, ownerLogin.authToken());
        ListGamesResult listResult = serializer.fromJson(
                listResponse.body(),
                ListGamesResult.class);
        List<GameSummary> expectedGames = List.of(new GameSummary(
                createdGame.gameID(),
                "owner",
                "opponent",
                "Championship Match"));

        Assertions.assertEquals(200, listResponse.statusCode());
        assertJsonContentType(listResponse);
        Assertions.assertEquals(expectedGames, listResult.games());

        HttpResponse<String> logoutResponse = sendRequest(
                serverUri.resolve("session"),
                "DELETE",
                null,
                ownerLogin.authToken(),
                "authorization");
        assertSuccessfulEmptyResponse(logoutResponse);

        HttpResponse<String> loggedOutSessionResponse = requestGames(
                serverUri,
                ownerLogin.authToken());
        assertErrorResponse(
                loggedOutSessionResponse,
                401,
                "Error: unauthorized");

        HttpResponse<String> independentSessionResponse = requestGames(
                serverUri,
                owner.authToken());
        Assertions.assertEquals(200, independentSessionResponse.statusCode());

        HttpResponse<String> clearResponse = sendRequest(
                serverUri.resolve("db"),
                "DELETE",
                null,
                null,
                null);
        assertSuccessfulEmptyResponse(clearResponse);

        HttpResponse<String> clearedSessionResponse = requestGames(
                serverUri,
                owner.authToken());
        assertErrorResponse(
                clearedSessionResponse,
                401,
                "Error: unauthorized");
    }

    @Test
    public void malformedJsonReturnsConsistentBadRequestResponses()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");
        String malformedJson = "{not-valid-json";

        List<HttpResponse<String>> responses = List.of(
                sendRequest(
                        serverUri.resolve("user"),
                        "POST",
                        malformedJson,
                        null,
                        null),
                sendRequest(
                        serverUri.resolve("session"),
                        "POST",
                        malformedJson,
                        null,
                        null),
                sendRequest(
                        serverUri.resolve("game"),
                        "POST",
                        malformedJson,
                        registeredUser.authToken(),
                        "authorization"),
                sendRequest(
                        serverUri.resolve("game"),
                        "PUT",
                        malformedJson,
                        registeredUser.authToken(),
                        "authorization"));

        for (HttpResponse<String> response : responses) {
            assertErrorResponse(response, 400, "Error: bad request");
        }
    }

    @Test
    public void emptyAndNullJsonBodiesReturnBadRequestResponses()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");

        for (String requestBody : List.of("", "null")) {
            List<HttpResponse<String>> responses = List.of(
                    sendRequest(
                            serverUri.resolve("user"),
                            "POST",
                            requestBody,
                            null,
                            null),
                    sendRequest(
                            serverUri.resolve("session"),
                            "POST",
                            requestBody,
                            null,
                            null),
                    sendRequest(
                            serverUri.resolve("game"),
                            "POST",
                            requestBody,
                            registeredUser.authToken(),
                            "authorization"),
                    sendRequest(
                            serverUri.resolve("game"),
                            "PUT",
                            requestBody,
                            registeredUser.authToken(),
                            "authorization"));

            for (HttpResponse<String> response : responses) {
                assertErrorResponse(response, 400, "Error: bad request");
            }
        }
    }

    @Test
    public void authorizationHeaderNameIsCaseInsensitive()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");
        CreateGameRequest createRequest = new CreateGameRequest(null, "Header Match");

        HttpResponse<String> createResponse = sendRequest(
                serverUri.resolve("game"),
                "POST",
                serializer.toJson(createRequest),
                registeredUser.authToken(),
                "Authorization");
        CreateGameResult createResult = serializer.fromJson(
                createResponse.body(),
                CreateGameResult.class);

        HttpResponse<String> listResponse = sendRequest(
                serverUri.resolve("game"),
                "GET",
                null,
                registeredUser.authToken(),
                "Authorization");
        ListGamesResult listResult = serializer.fromJson(
                listResponse.body(),
                ListGamesResult.class);

        Assertions.assertEquals(200, createResponse.statusCode());
        Assertions.assertEquals(200, listResponse.statusCode());
        Assertions.assertEquals(createResult.gameID(), listResult.games().getFirst().gameID());
    }

    @Test
    public void unexpectedFailureReturnsSafeInternalServerError()
            throws IOException, InterruptedException {
        Server server = new Server(
                new UnexpectedFailureUserDAO(),
                new MemoryAuthDAO(),
                new MemoryGameDAO());
        URI serverUri = startServer(server);
        RegisterRequest request = new RegisterRequest(
                "reader",
                "secret-password",
                "reader@example.com");

        HttpResponse<String> response = sendRequest(
                serverUri.resolve("user"),
                "POST",
                serializer.toJson(request),
                null,
                null);

        assertErrorResponse(
                response,
                500,
                "Error: internal server error");
        Assertions.assertFalse(response.body().contains("Sensitive internal detail"));
    }

    private HttpResponse<String> joinGame(
            URI serverUri,
            String authToken,
            ChessGame.TeamColor playerColor,
            int gameID) throws IOException, InterruptedException {
        JoinGameRequest request = new JoinGameRequest(null, playerColor, gameID);
        return sendRequest(
                serverUri.resolve("game"),
                "PUT",
                serializer.toJson(request),
                authToken,
                "authorization");
    }
}
