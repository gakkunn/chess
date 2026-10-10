package service.request;

/**
 * Contains the authenticated session and name for a new game.
 */
public record CreateGameRequest(String authToken, String gameName) {
}
