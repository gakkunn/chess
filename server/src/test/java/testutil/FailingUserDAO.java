package testutil;

import dataaccess.DataAccessException;
import dataaccess.UserDAO;
import model.UserData;

/**
 * Provides a UserDAO that always reports a data access failure.
 */
public class FailingUserDAO implements UserDAO {

    private final DataAccessException failure =
            new DataAccessException("User storage is unavailable");

    @Override
    public void clear() throws DataAccessException {
        throw failure;
    }

    @Override
    public void createUser(UserData userData) throws DataAccessException {
        throw failure;
    }

    @Override
    public UserData getUser(String username) throws DataAccessException {
        throw failure;
    }
}
