package testutil;

import dataaccess.UserDAO;
import model.UserData;

/**
 * Provides a UserDAO that fails with an unexpected runtime exception.
 */
public class UnexpectedFailureUserDAO implements UserDAO {

    private static final String INTERNAL_DETAIL = "Sensitive internal detail";

    @Override
    public void clear() {
        throw new IllegalStateException(INTERNAL_DETAIL);
    }

    @Override
    public void createUser(UserData userData) {
        throw new IllegalStateException(INTERNAL_DETAIL);
    }

    @Override
    public UserData getUser(String username) {
        throw new IllegalStateException(INTERNAL_DETAIL);
    }
}
