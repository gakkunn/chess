package service;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import dataaccess.UserDAO;
import service.request.ClearRequest;
import service.result.ClearResult;

/**
 * Coordinates removal of all authentication, game, and user data.
 */
public class ClearService {

    private final UserDAO userDAO;
    private final AuthDAO authDAO;
    private final GameDAO gameDAO;

    public ClearService(UserDAO userDAO, AuthDAO authDAO, GameDAO gameDAO) {
        this.userDAO = userDAO;
        this.authDAO = authDAO;
        this.gameDAO = gameDAO;
    }

    /**
     * Removes every stored session, game, and user.
     */
    public ClearResult clear(ClearRequest request) throws DataAccessException {
        authDAO.clear();
        gameDAO.clear();
        userDAO.clear();

        return new ClearResult();
    }
}
