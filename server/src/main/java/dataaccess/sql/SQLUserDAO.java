package dataaccess.sql;

import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import dataaccess.UserDAO;
import model.UserData;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

public class SQLUserDAO implements UserDAO {

    @Override
    public void clear() throws DataAccessException {
        String sql = "DELETE FROM users";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to clear stored users", exception);
        }
    }

    @Override
    public void createUser(UserData userData) throws DataAccessException {
        requireCompleteUserData(userData);

        String sql = """
                INSERT INTO users (username, password_hash, email)
                VALUES (?, ?, ?)
                """;
        String passwordHash = BCrypt.hashpw(userData.password(), BCrypt.gensalt());

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userData.username());
            statement.setString(2, passwordHash);
            statement.setString(3, userData.email());
            statement.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException exception) {
            throw new DataAccessException(
                    "A user with that username already exists",
                    exception);
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to store the user", exception);
        }
    }

    @Override
    public UserData getUser(String username) throws DataAccessException {
        String sql = """
                SELECT username, password_hash, email
                FROM users
                WHERE username = ?
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readUser(resultSet);
                }
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to read the user", exception);
        }

        return null;
    }

    @Override
    public boolean verifyCredentials(String username, String password)
            throws DataAccessException {
        if (password == null) {
            return false;
        }

        UserData storedUser = getUser(username);
        if (storedUser == null) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, storedUser.password());
        } catch (IllegalArgumentException exception) {
            throw new DataAccessException("Stored password hash is invalid", exception);
        }
    }

    private UserData readUser(ResultSet resultSet) throws SQLException {
        return new UserData(
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("email"));
    }

    private void requireCompleteUserData(UserData userData) throws DataAccessException {
        if (userData == null) {
            throw new DataAccessException("User data is required");
        }
        if (userData.username() == null) {
            throw new DataAccessException("A username is required to store a user");
        }
        if (userData.password() == null) {
            throw new DataAccessException("A password is required to store a user");
        }
        if (userData.email() == null) {
            throw new DataAccessException("An email address is required to store a user");
        }
    }
}
