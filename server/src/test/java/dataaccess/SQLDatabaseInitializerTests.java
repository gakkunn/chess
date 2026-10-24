package dataaccess;

import dataaccess.sql.SQLDatabaseInitializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SQLDatabaseInitializerTests {

    @BeforeEach
    public void resetSchema() throws DataAccessException, SQLException {
        DatabaseManager.createDatabase();

        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS games");
            statement.executeUpdate("DROP TABLE IF EXISTS auth");
            statement.executeUpdate("DROP TABLE IF EXISTS users");
        }
    }

    @Test
    public void initializeCreatesRequiredTables()
            throws DataAccessException, SQLException {
        SQLDatabaseInitializer.initialize();

        Set<String> tableNames = readTableNames();

        Assertions.assertEquals(Set.of("users", "auth", "games"), tableNames);
    }

    @Test
    public void tablesContainRequiredColumns()
            throws DataAccessException, SQLException {
        SQLDatabaseInitializer.initialize();

        Map<String, Set<String>> columnsByTable = readColumnsByTable();

        Assertions.assertEquals(
                Set.of("username", "password_hash", "email"),
                columnsByTable.get("users"));
        Assertions.assertEquals(
                Set.of("auth_token", "username"),
                columnsByTable.get("auth"));
        Assertions.assertEquals(
                Set.of(
                        "game_id",
                        "white_username",
                        "black_username",
                        "game_name",
                        "game_json"),
                columnsByTable.get("games"));
    }

    @Test
    public void tablesUseRequiredKeysAndNullability()
            throws DataAccessException, SQLException {
        SQLDatabaseInitializer.initialize();

        Map<String, ColumnDefinition> users = readColumnDefinitions("users");
        Map<String, ColumnDefinition> auth = readColumnDefinitions("auth");
        Map<String, ColumnDefinition> games = readColumnDefinitions("games");

        Assertions.assertEquals(
                new ColumnDefinition("varchar", false, "PRI", ""),
                users.get("username"));
        Assertions.assertEquals(
                new ColumnDefinition("varchar", false, "PRI", ""),
                auth.get("auth_token"));
        Assertions.assertEquals(
                new ColumnDefinition("int", false, "PRI", "auto_increment"),
                games.get("game_id"));
        Assertions.assertFalse(games.get("game_name").nullable());
        Assertions.assertFalse(games.get("game_json").nullable());
        Assertions.assertTrue(games.get("white_username").nullable());
        Assertions.assertTrue(games.get("black_username").nullable());
    }

    @Test
    public void tablesUseRequiredForeignKeys()
            throws DataAccessException, SQLException {
        SQLDatabaseInitializer.initialize();

        Set<ForeignKeyDefinition> authForeignKeys = readForeignKeys("auth");
        Set<ForeignKeyDefinition> gameForeignKeys = readForeignKeys("games");

        Assertions.assertEquals(
                Set.of(new ForeignKeyDefinition(
                        "username",
                        "users",
                        "username",
                        DatabaseMetaData.importedKeyCascade)),
                authForeignKeys);
        Assertions.assertEquals(
                Set.of(
                        new ForeignKeyDefinition(
                                "white_username",
                                "users",
                                "username",
                                DatabaseMetaData.importedKeySetNull),
                        new ForeignKeyDefinition(
                                "black_username",
                                "users",
                                "username",
                                DatabaseMetaData.importedKeySetNull)),
                gameForeignKeys);
    }

    @Test
    public void initializeCanRunMoreThanOnce() {
        Assertions.assertDoesNotThrow(() -> {
            SQLDatabaseInitializer.initialize();
            SQLDatabaseInitializer.initialize();
        });
    }

    private Set<String> readTableNames() throws DataAccessException, SQLException {
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """;
        Set<String> tableNames = new HashSet<>();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("table_name"));
            }
        }

        return tableNames;
    }

    private Map<String, Set<String>> readColumnsByTable()
            throws DataAccessException, SQLException {
        String sql = """
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                """;
        Map<String, Set<String>> columnsByTable = new HashMap<>();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("table_name");
                String columnName = resultSet.getString("column_name");
                columnsByTable
                        .computeIfAbsent(tableName, ignored -> new HashSet<>())
                        .add(columnName);
            }
        }

        return columnsByTable;
    }

    private Map<String, ColumnDefinition> readColumnDefinitions(String tableName)
            throws DataAccessException, SQLException {
        String sql = """
                SELECT column_name, data_type, is_nullable, column_key, extra
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """;
        Map<String, ColumnDefinition> columnDefinitions = new HashMap<>();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ColumnDefinition definition = new ColumnDefinition(
                            resultSet.getString("data_type"),
                            resultSet.getString("is_nullable").equals("YES"),
                            resultSet.getString("column_key"),
                            resultSet.getString("extra"));
                    columnDefinitions.put(
                            resultSet.getString("column_name"),
                            definition);
                }
            }
        }

        return columnDefinitions;
    }

    private Set<ForeignKeyDefinition> readForeignKeys(String tableName)
            throws DataAccessException, SQLException {
        Set<ForeignKeyDefinition> foreignKeys = new HashSet<>();

        try (Connection connection = DatabaseManager.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();

            try (ResultSet resultSet = metadata.getImportedKeys(
                    connection.getCatalog(),
                    null,
                    tableName)) {
                while (resultSet.next()) {
                    foreignKeys.add(new ForeignKeyDefinition(
                            resultSet.getString("FKCOLUMN_NAME"),
                            resultSet.getString("PKTABLE_NAME"),
                            resultSet.getString("PKCOLUMN_NAME"),
                            resultSet.getShort("DELETE_RULE")));
                }
            }
        }

        return foreignKeys;
    }

    private record ColumnDefinition(
            String dataType,
            boolean nullable,
            String key,
            String extra) {
    }

    private record ForeignKeyDefinition(
            String columnName,
            String referencedTable,
            String referencedColumn,
            int deleteRule) {
    }
}
