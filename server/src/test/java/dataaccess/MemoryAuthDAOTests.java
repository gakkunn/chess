package dataaccess;

import model.AuthData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MemoryAuthDAOTests {

    private AuthDAO authDAO;

    @BeforeEach
    public void setUp() {
        authDAO = new MemoryAuthDAO();
    }

    @Test
    public void createAuthStoresAuthenticatedSession() throws DataAccessException {
        AuthData authData = new AuthData("token-123", "reader");

        authDAO.createAuth(authData);

        Assertions.assertEquals(authData, authDAO.getAuth("token-123"));
    }

    @Test
    public void getAuthReturnsNullForUnknownToken() throws DataAccessException {
        AuthData missingAuth = authDAO.getAuth("missing-token");

        Assertions.assertNull(missingAuth);
    }

    @Test
    public void deleteAuthRemovesOnlyTheRequestedSession() throws DataAccessException {
        AuthData firstSession = new AuthData("first-token", "reader");
        AuthData secondSession = new AuthData("second-token", "reader");
        authDAO.createAuth(firstSession);
        authDAO.createAuth(secondSession);

        authDAO.deleteAuth("first-token");

        Assertions.assertNull(authDAO.getAuth("first-token"));
        Assertions.assertEquals(secondSession, authDAO.getAuth("second-token"));
    }

    @Test
    public void deleteAuthAllowsUnknownToken() {
        Assertions.assertDoesNotThrow(() -> authDAO.deleteAuth("missing-token"));
    }

    @Test
    public void clearRemovesEveryAuthenticatedSession() throws DataAccessException {
        authDAO.createAuth(new AuthData("first-token", "first-user"));
        authDAO.createAuth(new AuthData("second-token", "second-user"));

        authDAO.clear();

        Assertions.assertNull(authDAO.getAuth("first-token"));
        Assertions.assertNull(authDAO.getAuth("second-token"));
    }

    @Test
    public void createAuthRejectsDuplicateToken() throws DataAccessException {
        AuthData originalSession = new AuthData("shared-token", "first-user");
        AuthData duplicateSession = new AuthData("shared-token", "second-user");
        authDAO.createAuth(originalSession);

        Assertions.assertThrows(
                DataAccessException.class,
                () -> authDAO.createAuth(duplicateSession));
        Assertions.assertEquals(originalSession, authDAO.getAuth("shared-token"));
    }

    @Test
    public void createAuthRejectsMissingAuthData() {
        Assertions.assertThrows(
                DataAccessException.class,
                () -> authDAO.createAuth(null));
    }

    @Test
    public void createAuthRejectsMissingToken() {
        AuthData authWithoutToken = new AuthData(null, "reader");

        Assertions.assertThrows(
                DataAccessException.class,
                () -> authDAO.createAuth(authWithoutToken));
    }

    @Test
    public void createAuthRejectsMissingUsername() {
        AuthData authWithoutUsername = new AuthData("token-123", null);

        Assertions.assertThrows(
                DataAccessException.class,
                () -> authDAO.createAuth(authWithoutUsername));
    }
}
