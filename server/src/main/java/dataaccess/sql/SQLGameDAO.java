package dataaccess.sql;

import dataaccess.DataAccessException;
import dataaccess.DatabaseManager;
import dataaccess.GameDAO;
import model.GameData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLGameDAO implements GameDAO {

    private final ChessGameJsonSerializer gameSerializer =
            new ChessGameJsonSerializer();

    @Override
    public void clear() throws DataAccessException {
        try (Connection connection = DatabaseManager.getConnection()) {
            deleteGames(connection);
            resetGameIdentifierSequence(connection);
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to clear stored games", exception);
        }
    }

    @Override
    public int createGame(GameData gameData) throws DataAccessException {
        requireCompleteGameData(gameData);

        String sql = """
                INSERT INTO games (
                    white_username,
                    black_username,
                    game_name,
                    game_json
                )
                VALUES (?, ?, ?, ?)
                """;
        String gameJson = gameSerializer.serialize(gameData.game());

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     Statement.RETURN_GENERATED_KEYS)) {
            setGameFields(statement, gameData, gameJson);
            statement.executeUpdate();
            return readGeneratedGameID(statement);
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to store the game", exception);
        }
    }

    @Override
    public GameData getGame(int gameID) throws DataAccessException {
        String sql = """
                SELECT
                    game_id,
                    white_username,
                    black_username,
                    game_name,
                    game_json
                FROM games
                WHERE game_id = ?
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, gameID);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readGame(resultSet);
                }
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to read the game", exception);
        }

        return null;
    }

    @Override
    public List<GameData> listGames() throws DataAccessException {
        String sql = """
                SELECT
                    game_id,
                    white_username,
                    black_username,
                    game_name,
                    game_json
                FROM games
                ORDER BY game_id
                """;
        List<GameData> games = new ArrayList<>();

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                games.add(readGame(resultSet));
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to list stored games", exception);
        }

        return games;
    }

    @Override
    public void updateGame(GameData gameData) throws DataAccessException {
        requireCompleteGameData(gameData);
        requireStoredGameID(gameData.gameID());

        String sql = """
                UPDATE games
                SET white_username = ?,
                    black_username = ?,
                    game_name = ?,
                    game_json = ?
                WHERE game_id = ?
                """;
        String gameJson = gameSerializer.serialize(gameData.game());

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setGameFields(statement, gameData, gameJson);
            statement.setInt(5, gameData.gameID());

            int updatedRows = statement.executeUpdate();
            if (updatedRows == 0) {
                throw new DataAccessException(
                        "Cannot update a game that does not exist");
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Unable to update the game", exception);
        }
    }

    private void deleteGames(Connection connection) throws SQLException {
        String sql = "DELETE FROM games";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private void resetGameIdentifierSequence(Connection connection) throws SQLException {
        String sql = "ALTER TABLE games AUTO_INCREMENT = 1";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private void setGameFields(
            PreparedStatement statement,
            GameData gameData,
            String gameJson) throws SQLException {
        statement.setString(1, gameData.whiteUsername());
        statement.setString(2, gameData.blackUsername());
        statement.setString(3, gameData.gameName());
        statement.setString(4, gameJson);
    }

    private int readGeneratedGameID(PreparedStatement statement)
            throws SQLException, DataAccessException {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }

        throw new DataAccessException("The database did not assign a game identifier");
    }

    private GameData readGame(ResultSet resultSet)
            throws SQLException, DataAccessException {
        return new GameData(
                resultSet.getInt("game_id"),
                resultSet.getString("white_username"),
                resultSet.getString("black_username"),
                resultSet.getString("game_name"),
                gameSerializer.deserialize(resultSet.getString("game_json")));
    }

    private void requireCompleteGameData(GameData gameData)
            throws DataAccessException {
        if (gameData == null) {
            throw new DataAccessException("Game data is required");
        }
        if (gameData.gameName() == null) {
            throw new DataAccessException("A name is required to store a game");
        }
        if (gameData.game() == null) {
            throw new DataAccessException("A chess game is required");
        }
    }

    private void requireStoredGameID(int gameID) throws DataAccessException {
        if (gameID <= 0) {
            throw new DataAccessException(
                    "A positive game identifier is required to update a game");
        }
    }
}
