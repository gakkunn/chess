package service.result;

/**
 * Returns the logged-in username and its new authentication token.
 */
public record LoginResult(String username, String authToken) {
}
