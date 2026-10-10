package service.request;

/**
 * Identifies the authenticated session that should be logged out.
 */
public record LogoutRequest(String authToken) {
}
