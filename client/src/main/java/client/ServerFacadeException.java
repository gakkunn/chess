package client;

public class ServerFacadeException extends Exception {

    public ServerFacadeException(String message) {
        super(message);
    }

    public ServerFacadeException(String message, Throwable cause) {
        super(message, cause);
    }
}
