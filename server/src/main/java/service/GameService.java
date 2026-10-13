package service;

import chess.ChessGame;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import model.GameData;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.CreateGameRequest;
import service.result.CreateGameResult;

/**
 * Implements operations for creating and participating in chess games.
 */
public class GameService {

    private final AuthDAO authDAO;
    private final GameDAO gameDAO;

    public GameService(AuthDAO authDAO, GameDAO gameDAO) {
        this.authDAO = authDAO;
        this.gameDAO = gameDAO;
    }

    /**
     * Creates an unclaimed chess game for an authenticated user.
     */
    public CreateGameResult createGame(CreateGameRequest request)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        validateCreateGameRequest(request);
        requireAuthenticatedSession(request.authToken());

        GameData newGame = new GameData(
                0,
                null,
                null,
                request.gameName(),
                new ChessGame());
        int gameID = gameDAO.createGame(newGame);

        return new CreateGameResult(gameID);
    }

    private void validateCreateGameRequest(CreateGameRequest request)
            throws BadRequestException {
        if (request == null || isMissing(request.gameName())) {
            throw new BadRequestException();
        }
    }

    private void requireAuthenticatedSession(String authToken)
            throws UnauthorizedException, DataAccessException {
        if (isMissing(authToken) || authDAO.getAuth(authToken) == null) {
            throw new UnauthorizedException();
        }
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank();
    }
}
