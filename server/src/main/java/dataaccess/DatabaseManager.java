package dataaccess;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseManager {

    private static String databaseName;
    private static String databaseUsername;
    private static String databasePassword;
    private static String connectionUrl;

    static {
        loadPropertiesFromResources();
    }

    public static void createDatabase() throws DataAccessException {
        String statement = "CREATE DATABASE IF NOT EXISTS " + quoteIdentifier(databaseName);

        try (Connection connection = DriverManager.getConnection(
                connectionUrl,
                databaseUsername,
                databasePassword);
             var preparedStatement = connection.prepareStatement(statement)) {
            preparedStatement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to create the database", exception);
        }
    }

    public static Connection getConnection() throws DataAccessException {
        try {
            Connection connection = DriverManager.getConnection(
                    connectionUrl,
                    databaseUsername,
                    databasePassword);
            connection.setCatalog(databaseName);
            return connection;
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to connect to the database", exception);
        }
    }

    private static void loadPropertiesFromResources() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream propertyStream = classLoader.getResourceAsStream("db.properties")) {
            if (propertyStream == null) {
                throw new IllegalStateException("Unable to find db.properties");
            }

            Properties properties = new Properties();
            properties.load(propertyStream);
            loadProperties(properties);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read db.properties", exception);
        }
    }

    private static void loadProperties(Properties properties) {
        databaseName = requireProperty(properties, "db.name");
        databaseUsername = requireProperty(properties, "db.user");
        databasePassword = requireProperty(properties, "db.password");

        String databaseHost = requireProperty(properties, "db.host");
        String databasePort = requireProperty(properties, "db.port");
        connectionUrl = "jdbc:mysql://" + databaseHost + ":" + databasePort;
    }

    private static String requireProperty(Properties properties, String propertyName) {
        String propertyValue = properties.getProperty(propertyName);

        if (propertyValue == null) {
            throw new IllegalStateException("Missing database property: " + propertyName);
        }

        return propertyValue;
    }

    private static String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
