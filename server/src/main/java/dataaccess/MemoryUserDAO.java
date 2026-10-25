package dataaccess;

import model.UserData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores registered users in memory for the lifetime of the server process.
 */
public class MemoryUserDAO implements UserDAO {

    private final Map<String, UserData> usersByUsername = new HashMap<>();

    @Override
    public synchronized void clear() {
        usersByUsername.clear();
    }

    @Override
    public synchronized void createUser(UserData userData) throws DataAccessException {
        String username = requireUsername(userData);

        if (usersByUsername.containsKey(username)) {
            throw new DataAccessException("A user with that username already exists");
        }

        usersByUsername.put(username, userData);
    }

    @Override
    public synchronized UserData getUser(String username) {
        return usersByUsername.get(username);
    }

    @Override
    public synchronized boolean verifyCredentials(String username, String password) {
        UserData storedUser = usersByUsername.get(username);

        return storedUser != null
                && Objects.equals(storedUser.password(), password);
    }

    private String requireUsername(UserData userData) throws DataAccessException {
        if (userData == null) {
            throw new DataAccessException("User data is required");
        }

        if (userData.username() == null) {
            throw new DataAccessException("A username is required to store a user");
        }

        return userData.username();
    }
}
