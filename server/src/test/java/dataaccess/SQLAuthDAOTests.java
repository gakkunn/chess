package dataaccess;

import dataaccess.sql.SQLAuthDAO;
import dataaccess.sql.SQLUserDAO;
import model.AuthData;
import model.UserData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SQLAuthDAOTests extends SQLDAOTestSupport {

    private static final UserData STORED_USER = new UserData(
            "reader",
            "secret-password",
            "reader@example.com");

    private AuthDAO authDAO;
    private UserDAO userDAO;

    @BeforeEach
    public void setUpAuthDAO() throws DataAccessException {
        authDAO = new SQLAuthDAO();
        userDAO = new SQLUserDAO();
        userDAO.createUser(STORED_USER);
    }

    @Test
    public void createAuthStoresSessionAcrossDAOInstances() throws DataAccessException {
        AuthData authData = new AuthData("token-123", STORED_USER.username());

        authDAO.createAuth(authData);
        AuthData storedAuth = new SQLAuthDAO().getAuth("token-123");

        Assertions.assertEquals(authData, storedAuth);
    }

    @Test
    public void getAuthReturnsNullForUnknownToken() throws DataAccessException {
        AuthData missingAuth = authDAO.getAuth("missing-token");

        Assertions.assertNull(missingAuth);
    }

    @Test
    public void deleteAuthRemovesOnlyRequestedSession() throws DataAccessException {
        AuthData firstSession = new AuthData("first-token", STORED_USER.username());
        AuthData secondSession = new AuthData("second-token", STORED_USER.username());
        authDAO.createAuth(firstSession);
        authDAO.createAuth(secondSession);

        authDAO.deleteAuth(firstSession.authToken());

        Assertions.assertNull(authDAO.getAuth(firstSession.authToken()));
        Assertions.assertEquals(
                secondSession,
                authDAO.getAuth(secondSession.authToken()));
    }

    @Test
    public void deleteAuthAllowsUnknownToken() {
        Assertions.assertDoesNotThrow(() -> authDAO.deleteAuth("missing-token"));
    }

    @Test
    public void clearRemovesSessionsWithoutRemovingUsers() throws DataAccessException {
        authDAO.createAuth(new AuthData("first-token", STORED_USER.username()));
        authDAO.createAuth(new AuthData("second-token", STORED_USER.username()));

        authDAO.clear();

        Assertions.assertNull(authDAO.getAuth("first-token"));
        Assertions.assertNull(authDAO.getAuth("second-token"));
        Assertions.assertNotNull(userDAO.getUser(STORED_USER.username()));
    }

    @Test
    public void createAuthRejectsDuplicateToken() throws DataAccessException {
        AuthData originalSession = new AuthData("shared-token", STORED_USER.username());
        authDAO.createAuth(originalSession);

        Assertions.assertThrows(
                DataAccessException.class,
                () -> authDAO.createAuth(new AuthData(
                        "shared-token",
                        STORED_USER.username())));
        Assertions.assertEquals(
                originalSession,
                authDAO.getAuth(originalSession.authToken()));
    }

    @Test
    public void createAuthRejectsMissingAuthData() {
        Assertions.assertThrows(
                DataAccessException.class,
                () -> authDAO.createAuth(null));
    }

    @Test
    public void createAuthRejectsMissingRequiredFields() {
        AuthData[] incompleteSessions = {
                new AuthData(null, STORED_USER.username()),
                new AuthData("token-123", null)
        };

        for (AuthData incompleteSession : incompleteSessions) {
            Assertions.assertThrows(
                    DataAccessException.class,
                    () -> authDAO.createAuth(incompleteSession));
        }
    }

    @Test
    public void createAuthRejectsUnknownUsername() {
        AuthData sessionForUnknownUser = new AuthData(
                "token-123",
                "missing-user");

        Assertions.assertThrows(
                DataAccessException.class,
                () -> authDAO.createAuth(sessionForUnknownUser));
    }
}
