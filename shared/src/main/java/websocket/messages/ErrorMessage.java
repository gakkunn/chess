package websocket.messages;

public final class ErrorMessage extends ServerMessage {

    private final String errorMessage;

    public ErrorMessage(String errorMessage) {
        super(ServerMessageType.ERROR);

        if (errorMessage == null || errorMessage.isBlank()) {
            this.errorMessage = "Error: unable to complete the request.";
        } else if (errorMessage.startsWith("Error:")) {
            this.errorMessage = errorMessage;
        } else {
            this.errorMessage = "Error: " + errorMessage;
        }
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
