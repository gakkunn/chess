package dataaccess;

import model.GameData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores chess games in memory for the lifetime of the server process.
 */
public class MemoryGameDAO implements GameDAO {

    private final Map<Integer, GameData> gamesByID = new LinkedHashMap<>();
    private int nextGameID = 1;

    @Override
    public synchronized void clear() {
        gamesByID.clear();
        nextGameID = 1;
    }

    @Override
    public synchronized int createGame(GameData gameData) throws DataAccessException {
        requireGameData(gameData);

        int gameID = nextGameID;
        nextGameID++;

        GameData storedGame = new GameData(
                gameID,
                gameData.whiteUsername(),
                gameData.blackUsername(),
                gameData.gameName(),
                gameData.game());

        gamesByID.put(gameID, storedGame);
        return gameID;
    }

    @Override
    public synchronized GameData getGame(int gameID) {
        return gamesByID.get(gameID);
    }

    @Override
    public synchronized List<GameData> listGames() {
        return new ArrayList<>(gamesByID.values());
    }

    @Override
    public synchronized void updateGame(GameData gameData) throws DataAccessException {
        requireGameData(gameData);

        if (!gamesByID.containsKey(gameData.gameID())) {
            throw new DataAccessException("Cannot update a game that does not exist");
        }

        gamesByID.put(gameData.gameID(), gameData);
    }

    private void requireGameData(GameData gameData) throws DataAccessException {
        if (gameData == null) {
            throw new DataAccessException("Game data is required");
        }
    }
}
