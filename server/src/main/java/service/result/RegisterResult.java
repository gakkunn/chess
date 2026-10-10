package service.result;

/**
 * Returns the registered username and its new authentication token.
 */
public record RegisterResult(String username, String authToken) {
}
