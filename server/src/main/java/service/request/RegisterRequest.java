package service.request;

/**
 * Contains the account information required to register a user.
 */
public record RegisterRequest(String username, String password, String email) {
}
