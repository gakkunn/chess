package dataaccess;

import model.UserData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MemoryUserDAOTests {

    private UserDAO userDAO;

    @BeforeEach
    public void setUp() {
        userDAO = new MemoryUserDAO();
    }

    @Test
    public void createUserStoresUser() throws DataAccessException {
        UserData user = new UserData("reader", "secret-password", "reader@example.com");

        userDAO.createUser(user);

        Assertions.assertEquals(user, userDAO.getUser("reader"));
    }

    @Test
    public void getUserReturnsNullForUnknownUsername() throws DataAccessException {
        UserData missingUser = userDAO.getUser("missing-user");

        Assertions.assertNull(missingUser);
    }

    @Test
    public void verifyCredentialsAcceptsMatchingPassword() throws DataAccessException {
        userDAO.createUser(new UserData(
                "reader",
                "secret-password",
                "reader@example.com"));

        boolean credentialsAreValid = userDAO.verifyCredentials(
                "reader",
                "secret-password");

        Assertions.assertTrue(credentialsAreValid);
    }

    @Test
    public void verifyCredentialsRejectsIncorrectPassword() throws DataAccessException {
        userDAO.createUser(new UserData(
                "reader",
                "secret-password",
                "reader@example.com"));

        boolean credentialsAreValid = userDAO.verifyCredentials(
                "reader",
                "incorrect-password");

        Assertions.assertFalse(credentialsAreValid);
    }

    @Test
    public void verifyCredentialsRejectsUnknownUsername() throws DataAccessException {
        boolean credentialsAreValid = userDAO.verifyCredentials(
                "missing-user",
                "secret-password");

        Assertions.assertFalse(credentialsAreValid);
    }

    @Test
    public void clearRemovesEveryUser() throws DataAccessException {
        userDAO.createUser(new UserData("first-user", "first-password", "first@example.com"));
        userDAO.createUser(new UserData("second-user", "second-password", "second@example.com"));

        userDAO.clear();

        Assertions.assertNull(userDAO.getUser("first-user"));
        Assertions.assertNull(userDAO.getUser("second-user"));
    }

    @Test
    public void createUserRejectsDuplicateUsername() throws DataAccessException {
        UserData originalUser = new UserData("reader", "first-password", "first@example.com");
        UserData duplicateUser = new UserData("reader", "second-password", "second@example.com");
        userDAO.createUser(originalUser);

        Assertions.assertThrows(
                DataAccessException.class,
                () -> userDAO.createUser(duplicateUser));
        Assertions.assertEquals(originalUser, userDAO.getUser("reader"));
    }

    @Test
    public void createUserRejectsMissingUserData() {
        Assertions.assertThrows(
                DataAccessException.class,
                () -> userDAO.createUser(null));
    }

    @Test
    public void createUserRejectsMissingUsername() {
        UserData userWithoutUsername = new UserData(null, "secret-password", "reader@example.com");

        Assertions.assertThrows(
                DataAccessException.class,
                () -> userDAO.createUser(userWithoutUsername));
    }
}
