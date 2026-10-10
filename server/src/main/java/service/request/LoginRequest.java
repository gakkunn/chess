package service.request;

/**
 * Contains the credentials required to log in a user.
 */
public record LoginRequest(String username, String password) {
}
