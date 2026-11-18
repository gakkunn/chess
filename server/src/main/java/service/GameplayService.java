package service;

import chess.ChessGame;
import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import model.AuthData;
import model.GameData;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.request.ConnectGameRequest;
import service.result.ConnectGameResult;

public class GameplayService {

    private final AuthDAO authDAO;
    private final GameDAO gameDAO;

    public GameplayService(AuthDAO authDAO, GameDAO gameDAO) {
        this.authDAO = authDAO;
        this.gameDAO = gameDAO;
    }

    public ConnectGameResult connect(ConnectGameRequest request)
            throws BadRequestException, UnauthorizedException, DataAccessException {
        if (request == null || request.gameID() == null || request.gameID() <= 0) {
            throw new BadRequestException();
        }

        if (request.authToken() == null || request.authToken().isBlank()) {
            throw new UnauthorizedException();
        }

        AuthData auth = authDAO.getAuth(request.authToken());
        if (auth == null) {
            throw new UnauthorizedException();
        }

        GameData game = gameDAO.getGame(request.gameID());
        if (game == null) {
            throw new BadRequestException();
        }

        ChessGame.TeamColor color = null;
        if (auth.username().equals(game.whiteUsername())) {
            color = ChessGame.TeamColor.WHITE;
        } else if (auth.username().equals(game.blackUsername())) {
            color = ChessGame.TeamColor.BLACK;
        }

        return new ConnectGameResult(auth.username(), game, color);
    }
}
