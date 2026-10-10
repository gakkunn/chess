package service.request;

/**
 * Identifies the authenticated user requesting the available games.
 */
public record ListGamesRequest(String authToken) {
}
