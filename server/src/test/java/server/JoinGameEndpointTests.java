package server;

import chess.ChessGame;
import com.google.gson.JsonObject;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.MemoryAuthDAO;
import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import model.AuthData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.request.JoinGameRequest;
import service.result.CreateGameResult;
import service.result.ListGamesResult;
import service.result.RegisterResult;
import testutil.FailingAuthDAO;
import testutil.FailingGameDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;

public class JoinGameEndpointTests extends EndpointTestSupport {

    @Test
    public void joinGameAssignsAuthenticatedUserToRequestedColor()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");
        CreateGameResult createdGame = createGameSuccessfully(
                serverUri,
                registeredUser.authToken(),
                "Evening Match");

        HttpResponse<String> response = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(
                        null,
                        ChessGame.TeamColor.WHITE,
                        createdGame.gameID()));
        JsonObject responseBody = serializer.fromJson(response.body(), JsonObject.class);
        ListGamesResult listedGames = listGamesSuccessfully(
                serverUri,
                registeredUser.authToken());

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(responseBody.isEmpty());
        Assertions.assertEquals("reader", listedGames.games().getFirst().whiteUsername());
        Assertions.assertNull(listedGames.games().getFirst().blackUsername());
    }

    @Test
    public void joinGameAllowsSameUserToClaimBothColors()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");
        CreateGameResult createdGame = createGameSuccessfully(
                serverUri,
                registeredUser.authToken(),
                "Solo Match");

        HttpResponse<String> whiteResponse = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(
                        null,
                        ChessGame.TeamColor.WHITE,
                        createdGame.gameID()));
        HttpResponse<String> blackResponse = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(
                        null,
                        ChessGame.TeamColor.BLACK,
                        createdGame.gameID()));

        ListGamesResult listedGames = listGamesSuccessfully(
                serverUri,
                registeredUser.authToken());
        Assertions.assertEquals(200, whiteResponse.statusCode());
        Assertions.assertEquals(200, blackResponse.statusCode());
        Assertions.assertEquals("reader", listedGames.games().getFirst().whiteUsername());
        Assertions.assertEquals("reader", listedGames.games().getFirst().blackUsername());
    }

    @Test
    public void joinGameRejectsMissingPlayerColor()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");
        CreateGameResult createdGame = createGameSuccessfully(
                serverUri,
                registeredUser.authToken(),
                "Evening Match");

        HttpResponse<String> response = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(null, null, createdGame.gameID()));
        assertErrorResponse(response, 400, "Error: bad request");
    }

    @Test
    public void joinGameRejectsMissingGameIdentifier()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");

        HttpResponse<String> response = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, null));
        assertErrorResponse(response, 400, "Error: bad request");
    }

    @Test
    public void joinGameRejectsUnknownGameIdentifier()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult registeredUser = registerSuccessfully(
                serverUri,
                "reader",
                "reader@example.com");

        HttpResponse<String> response = joinGame(
                serverUri,
                registeredUser.authToken(),
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, 999));
        assertErrorResponse(response, 400, "Error: bad request");
    }

    @Test
    public void joinGameRejectsMissingAuthToken()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());

        HttpResponse<String> response = joinGame(
                serverUri,
                null,
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, 1));
        assertErrorResponse(response, 401, "Error: unauthorized");
    }

    @Test
    public void joinGameRejectsOccupiedColor()
            throws IOException, InterruptedException {
        URI serverUri = startServer(new Server());
        RegisterResult firstUser = registerSuccessfully(
                serverUri,
                "first-user",
                "first@example.com");
        RegisterResult secondUser = registerSuccessfully(
                serverUri,
                "second-user",
                "second@example.com");
        CreateGameResult createdGame = createGameSuccessfully(
                serverUri,
                firstUser.authToken(),
                "Contested Match");
        JoinGameRequest request = new JoinGameRequest(
                null,
                ChessGame.TeamColor.BLACK,
                createdGame.gameID());
        joinGame(serverUri, firstUser.authToken(), request);

        HttpResponse<String> response = joinGame(
                serverUri,
                secondUser.authToken(),
                request);
        assertErrorResponse(response, 403, "Error: already taken");
    }

    @Test
    public void joinGameAuthenticationFailureReturnsInternalServerError()
            throws IOException, InterruptedException {
        Server server = new Server(
                new MemoryUserDAO(),
                new FailingAuthDAO(),
                new MemoryGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = joinGame(
                serverUri,
                "valid-looking-token",
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, 1));
        assertErrorResponse(
                response,
                500,
                "Error: Authentication storage is unavailable");
    }

    @Test
    public void joinGameStorageFailureReturnsInternalServerError()
            throws DataAccessException, IOException, InterruptedException {
        AuthDAO authDAO = new MemoryAuthDAO();
        authDAO.createAuth(new AuthData("valid-token", "reader"));
        Server server = new Server(
                new MemoryUserDAO(),
                authDAO,
                new FailingGameDAO());
        URI serverUri = startServer(server);

        HttpResponse<String> response = joinGame(
                serverUri,
                "valid-token",
                new JoinGameRequest(null, ChessGame.TeamColor.WHITE, 1));
        assertErrorResponse(response, 500, "Error: Game storage is unavailable");
    }

    private HttpResponse<String> joinGame(
            URI serverUri,
            String authToken,
            JoinGameRequest request) throws IOException, InterruptedException {
        return sendRequest(
                serverUri.resolve("game"),
                "PUT",
                serializer.toJson(request),
                authToken);
    }
}
