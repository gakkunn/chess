package service.result;

import java.util.List;

/**
 * Returns the games visible to an authenticated user.
 */
public record ListGamesResult(List<GameSummary> games) {
}
