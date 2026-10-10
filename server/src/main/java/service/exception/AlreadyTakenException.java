package service.exception;

/**
 * Indicates that a requested username or player color is already in use.
 */
public class AlreadyTakenException extends Exception {

    public AlreadyTakenException() {
        super("Error: already taken");
    }
}
