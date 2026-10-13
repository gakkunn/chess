package testutil;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import model.AuthData;

/**
 * Provides an AuthDAO that always reports a data access failure.
 */
public class FailingAuthDAO implements AuthDAO {

    private final DataAccessException failure =
            new DataAccessException("Authentication storage is unavailable");

    @Override
    public void clear() throws DataAccessException {
        throw failure;
    }

    @Override
    public void createAuth(AuthData authData) throws DataAccessException {
        throw failure;
    }

    @Override
    public AuthData getAuth(String authToken) throws DataAccessException {
        throw failure;
    }

    @Override
    public void deleteAuth(String authToken) throws DataAccessException {
        throw failure;
    }
}
