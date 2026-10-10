package service.exception;

/**
 * Indicates that a request is missing required or valid input.
 */
public class BadRequestException extends Exception {

    public BadRequestException() {
        super("Error: bad request");
    }
}
