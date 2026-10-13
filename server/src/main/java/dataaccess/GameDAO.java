package dataaccess;

import model.GameData;

import java.util.List;

/**
 * Defines the operations used to store and retrieve chess games.
 */
public interface GameDAO {

    /**
     * Removes every stored game.
     */
    void clear() throws DataAccessException;

    /**
     * Stores a new game and returns its assigned identifier.
     */
    int createGame(GameData gameData) throws DataAccessException;

    /**
     * Finds a game by identifier, or returns null when the game does not exist.
     */
    GameData getGame(int gameID) throws DataAccessException;

    /**
     * Returns a snapshot containing every stored game.
     */
    List<GameData> listGames() throws DataAccessException;

    /**
     * Replaces an existing game with its updated data.
     */
    void updateGame(GameData gameData) throws DataAccessException;
}
