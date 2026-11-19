package server.websocket;

import dataaccess.DataAccessException;
import dataaccess.MemoryGameDAO;
import model.GameData;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ControlledGameDAO extends MemoryGameDAO {
    volatile boolean failUpdates;
    final AtomicBoolean blockNextUpdate = new AtomicBoolean();
    final CountDownLatch saveStarted = new CountDownLatch(1);
    final CountDownLatch allowSave = new CountDownLatch(1);

    @Override
    public void updateGame(GameData game) throws DataAccessException {
        if (blockNextUpdate.compareAndSet(true, false)) {
            saveStarted.countDown();
            try {
                if (!allowSave.await(5, TimeUnit.SECONDS)) {
                    throw new DataAccessException("Timed out waiting to save the game.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new DataAccessException("Interrupted while saving the game.", exception);
            }
        }
        if (failUpdates) {
            throw new DataAccessException("Unable to save the game.");
        }
        super.updateGame(game);
    }
}
