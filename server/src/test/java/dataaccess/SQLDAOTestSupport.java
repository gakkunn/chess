package dataaccess;

import dataaccess.sql.SQLDatabaseInitializer;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

abstract class SQLDAOTestSupport {

    private static final List<String> CLEAR_DATABASE_STATEMENTS = List.of(
            "DELETE FROM games",
            "DELETE FROM auth",
            "DELETE FROM users",
            "ALTER TABLE games AUTO_INCREMENT = 1");

    @BeforeEach
    public void clearDatabase() throws DataAccessException, SQLException {
        SQLDatabaseInitializer.initialize();

        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : CLEAR_DATABASE_STATEMENTS) {
                statement.executeUpdate(sql);
            }
        }
    }
}
