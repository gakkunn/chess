package dataaccess;

import model.AuthData;

/**
 * Defines the operations used to store and retrieve authenticated sessions.
 */
public interface AuthDAO {

    /**
     * Removes every authenticated session.
     */
    void clear() throws DataAccessException;

    /**
     * Stores a new authenticated session.
     */
    void createAuth(AuthData authData) throws DataAccessException;

    /**
     * Finds a session by authentication token, or returns null when it does not exist.
     */
    AuthData getAuth(String authToken) throws DataAccessException;

    /**
     * Removes the session associated with an authentication token.
     */
    void deleteAuth(String authToken) throws DataAccessException;
}
