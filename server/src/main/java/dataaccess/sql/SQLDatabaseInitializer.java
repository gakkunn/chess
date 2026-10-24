package dataaccess.sql;

import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class SQLDatabaseInitializer {

    private static final List<String> TABLE_CREATION_STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS users (
                username VARCHAR(256) NOT NULL,
                password_hash VARCHAR(60) NOT NULL,
                email VARCHAR(256) NOT NULL,
                PRIMARY KEY (username)
            ) ENGINE=InnoDB
            """,
            """
            CREATE TABLE IF NOT EXISTS auth (
                auth_token VARCHAR(256) NOT NULL,
                username VARCHAR(256) NOT NULL,
                PRIMARY KEY (auth_token),
                CONSTRAINT auth_username_fk
                    FOREIGN KEY (username) REFERENCES users (username)
                    ON DELETE CASCADE
            ) ENGINE=InnoDB
            """,
            """
            CREATE TABLE IF NOT EXISTS games (
                game_id INT NOT NULL AUTO_INCREMENT,
                white_username VARCHAR(256),
                black_username VARCHAR(256),
                game_name VARCHAR(256) NOT NULL,
                game_json TEXT NOT NULL,
                PRIMARY KEY (game_id),
                CONSTRAINT games_white_username_fk
                    FOREIGN KEY (white_username) REFERENCES users (username)
                    ON DELETE SET NULL,
                CONSTRAINT games_black_username_fk
                    FOREIGN KEY (black_username) REFERENCES users (username)
                    ON DELETE SET NULL
            ) ENGINE=InnoDB
            """);

    private SQLDatabaseInitializer() {
    }

    public static void initialize() throws DataAccessException {
        DatabaseManager.createDatabase();

        try (Connection connection = DatabaseManager.getConnection()) {
            for (String statement : TABLE_CREATION_STATEMENTS) {
                executeStatement(connection, statement);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to initialize database tables", exception);
        }
    }

    private static void executeStatement(Connection connection, String statement)
            throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(statement)) {
            preparedStatement.executeUpdate();
        }
    }
}
