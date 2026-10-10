package service.exception;

/**
 * Indicates that a request does not contain valid authentication.
 */
public class UnauthorizedException extends Exception {

    public UnauthorizedException() {
        super("Error: unauthorized");
    }
}
