package testutil;

import dataaccess.DataAccessException;
import dataaccess.GameDAO;
import model.GameData;

import java.util.List;

/**
 * Provides a GameDAO that always reports a data access failure.
 */
public class FailingGameDAO implements GameDAO {

    private final DataAccessException failure =
            new DataAccessException("Game storage is unavailable");

    @Override
    public void clear() throws DataAccessException {
        throw failure;
    }

    @Override
    public int createGame(GameData gameData) throws DataAccessException {
        throw failure;
    }

    @Override
    public GameData getGame(int gameID) throws DataAccessException {
        throw failure;
    }

    @Override
    public List<GameData> listGames() throws DataAccessException {
        throw failure;
    }

    @Override
    public void updateGame(GameData gameData) throws DataAccessException {
        throw failure;
    }
}
