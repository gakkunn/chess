package model;

/**
 * Associates an authentication token with the username that owns it.
 */
public record AuthData(String authToken, String username) {
}
