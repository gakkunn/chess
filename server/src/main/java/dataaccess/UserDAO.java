package dataaccess;

import model.UserData;

/**
 * Defines the operations used to store and retrieve registered users.
 */
public interface UserDAO {

    /**
     * Removes every registered user.
     */
    void clear() throws DataAccessException;

    /**
     * Stores a new user.
     */
    void createUser(UserData userData) throws DataAccessException;

    /**
     * Finds a user by username, or returns null when the user does not exist.
     */
    UserData getUser(String username) throws DataAccessException;
}
