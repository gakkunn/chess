package service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a stable lock shared by all operations on the same game.
 */
public final class GameLockManager {

    private final Map<Integer, Object> locks = new ConcurrentHashMap<>();

    public Object forGame(int gameID) {
        return locks.computeIfAbsent(gameID, ignored -> new Object());
    }
}
