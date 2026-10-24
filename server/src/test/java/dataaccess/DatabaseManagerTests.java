package dataaccess;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseManagerTests {

    @Test
    public void createDatabaseAllowsAConnectionToBeOpened()
            throws DataAccessException, SQLException {
        DatabaseManager.createDatabase();

        try (Connection connection = DatabaseManager.getConnection()) {
            Assertions.assertFalse(connection.isClosed());
            Assertions.assertNotNull(connection.getCatalog());
        }
    }

    @Test
    public void databaseConnectionCanExecuteAQuery()
            throws DataAccessException, SQLException {
        DatabaseManager.createDatabase();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 + 1 AS total");
             ResultSet resultSet = statement.executeQuery()) {
            Assertions.assertTrue(resultSet.next());
            Assertions.assertEquals(2, resultSet.getInt("total"));
        }
    }

    @Test
    public void createDatabaseCanRunMoreThanOnce() {
        Assertions.assertDoesNotThrow(() -> {
            DatabaseManager.createDatabase();
            DatabaseManager.createDatabase();
        });
    }
}
