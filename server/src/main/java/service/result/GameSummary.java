package service.result;

/**
 * Describes a game without exposing its internal chess state.
 */
public record GameSummary(
        int gameID,
        String whiteUsername,
        String blackUsername,
        String gameName) {
}
