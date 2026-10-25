package dataaccess;

import dataaccess.sql.SQLUserDAO;
import model.UserData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SQLUserDAOTests extends SQLDAOTestSupport {

    private UserDAO userDAO;

    @BeforeEach
    public void setUpUserDAO() {
        userDAO = new SQLUserDAO();
    }

    @Test
    public void createUserStoresUserWithHashedPassword() throws DataAccessException {
        UserData user = createUser("reader", "secret-password", "reader@example.com");

        userDAO.createUser(user);
        UserData storedUser = userDAO.getUser("reader");

        Assertions.assertNotNull(storedUser);
        Assertions.assertEquals(user.username(), storedUser.username());
        Assertions.assertEquals(user.email(), storedUser.email());
        Assertions.assertNotEquals(user.password(), storedUser.password());
        Assertions.assertTrue(BCrypt.checkpw(user.password(), storedUser.password()));
    }

    @Test
    public void getUserReturnsNullForUnknownUsername() throws DataAccessException {
        UserData missingUser = userDAO.getUser("missing-user");

        Assertions.assertNull(missingUser);
    }

    @Test
    public void verifyCredentialsAcceptsMatchingPassword() throws DataAccessException {
        userDAO.createUser(createUser(
                "reader",
                "secret-password",
                "reader@example.com"));

        boolean credentialsAreValid = userDAO.verifyCredentials(
                "reader",
                "secret-password");

        Assertions.assertTrue(credentialsAreValid);
    }

    @Test
    public void verifyCredentialsRejectsInvalidCredentials() throws DataAccessException {
        userDAO.createUser(createUser(
                "reader",
                "secret-password",
                "reader@example.com"));

        Assertions.assertFalse(userDAO.verifyCredentials(
                "reader",
                "incorrect-password"));
        Assertions.assertFalse(userDAO.verifyCredentials(
                "missing-user",
                "secret-password"));
        Assertions.assertFalse(userDAO.verifyCredentials("reader", null));
    }

    @Test
    public void verifyCredentialsReportsInvalidStoredPasswordHash()
            throws DataAccessException, SQLException {
        insertUserWithPasswordHash("reader", "invalid-hash", "reader@example.com");

        DataAccessException exception = Assertions.assertThrows(
                DataAccessException.class,
                () -> userDAO.verifyCredentials("reader", "secret-password"));

        Assertions.assertEquals("Stored password hash is invalid", exception.getMessage());
    }

    @Test
    public void createUserRejectsDuplicateUsername() throws DataAccessException {
        UserData originalUser = createUser(
                "reader",
                "first-password",
                "first@example.com");
        UserData duplicateUser = createUser(
                "reader",
                "second-password",
                "second@example.com");
        userDAO.createUser(originalUser);

        Assertions.assertThrows(
                DataAccessException.class,
                () -> userDAO.createUser(duplicateUser));
        Assertions.assertTrue(userDAO.verifyCredentials("reader", "first-password"));
        Assertions.assertFalse(userDAO.verifyCredentials("reader", "second-password"));
        Assertions.assertEquals(
                originalUser.email(),
                userDAO.getUser("reader").email());
    }

    @Test
    public void clearRemovesEveryUser() throws DataAccessException {
        userDAO.createUser(createUser(
                "first-user",
                "first-password",
                "first@example.com"));
        userDAO.createUser(createUser(
                "second-user",
                "second-password",
                "second@example.com"));

        userDAO.clear();

        Assertions.assertNull(userDAO.getUser("first-user"));
        Assertions.assertNull(userDAO.getUser("second-user"));
    }

    @Test
    public void createUserRejectsMissingUserData() {
        Assertions.assertThrows(
                DataAccessException.class,
                () -> userDAO.createUser(null));
    }

    @Test
    public void createUserRejectsMissingRequiredFields() {
        UserData[] incompleteUsers = {
                createUser(null, "secret-password", "reader@example.com"),
                createUser("reader", null, "reader@example.com"),
                createUser("reader", "secret-password", null)
        };

        for (UserData incompleteUser : incompleteUsers) {
            Assertions.assertThrows(
                    DataAccessException.class,
                    () -> userDAO.createUser(incompleteUser));
        }
    }

    private UserData createUser(String username, String password, String email) {
        return new UserData(username, password, email);
    }

    private void insertUserWithPasswordHash(
            String username,
            String passwordHash,
            String email) throws DataAccessException, SQLException {
        String sql = """
                INSERT INTO users (username, password_hash, email)
                VALUES (?, ?, ?)
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, email);
            statement.executeUpdate();
        }
    }
}
