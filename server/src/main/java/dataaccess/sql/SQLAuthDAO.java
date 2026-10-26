package dataaccess.sql;

import dataaccess.AuthDAO;
import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import model.AuthData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SQLAuthDAO implements AuthDAO {

    @Override
    public void clear() throws DataAccessException {
        String sql = "DELETE FROM auth";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "Unable to clear authenticated sessions",
                    exception);
        }
    }

    @Override
    public void createAuth(AuthData authData) throws DataAccessException {
        requireCompleteAuthData(authData);

        String sql = """
                INSERT INTO auth (auth_token, username)
                VALUES (?, ?)
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, authData.authToken());
            statement.setString(2, authData.username());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "Unable to store the authenticated session",
                    exception);
        }
    }

    @Override
    public AuthData getAuth(String authToken) throws DataAccessException {
        String sql = """
                SELECT auth_token, username
                FROM auth
                WHERE auth_token = ?
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, authToken);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new AuthData(
                            resultSet.getString("auth_token"),
                            resultSet.getString("username"));
                }
            }
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "Unable to read the authenticated session",
                    exception);
        }

        return null;
    }

    @Override
    public void deleteAuth(String authToken) throws DataAccessException {
        String sql = "DELETE FROM auth WHERE auth_token = ?";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, authToken);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "Unable to delete the authenticated session",
                    exception);
        }
    }

    private void requireCompleteAuthData(AuthData authData) throws DataAccessException {
        if (authData == null) {
            throw new DataAccessException("Authentication data is required");
        }
        if (authData.authToken() == null) {
            throw new DataAccessException("An authentication token is required");
        }
        if (authData.username() == null) {
            throw new DataAccessException(
                    "A username is required for an authenticated session");
        }
    }
}
