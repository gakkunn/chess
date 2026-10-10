package dataaccess;

import model.AuthData;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores authenticated sessions in memory for the lifetime of the server process.
 */
public class MemoryAuthDAO implements AuthDAO {

    private final Map<String, AuthData> sessionsByToken = new HashMap<>();

    @Override
    public synchronized void clear() {
        sessionsByToken.clear();
    }

    @Override
    public synchronized void createAuth(AuthData authData) throws DataAccessException {
        String authToken = requireCompleteAuthData(authData);

        if (sessionsByToken.containsKey(authToken)) {
            throw new DataAccessException("An authenticated session with that token already exists");
        }

        sessionsByToken.put(authToken, authData);
    }

    @Override
    public synchronized AuthData getAuth(String authToken) {
        return sessionsByToken.get(authToken);
    }

    @Override
    public synchronized void deleteAuth(String authToken) {
        sessionsByToken.remove(authToken);
    }

    private String requireCompleteAuthData(AuthData authData) throws DataAccessException {
        if (authData == null) {
            throw new DataAccessException("Authentication data is required");
        }

        if (authData.authToken() == null) {
            throw new DataAccessException("An authentication token is required");
        }

        if (authData.username() == null) {
            throw new DataAccessException("A username is required for an authenticated session");
        }

        return authData.authToken();
    }
}
