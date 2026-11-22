package websocket.messages;

public final class NotificationMessage extends ServerMessage {

    private final String message;
    private final Boolean gameOver;

    public NotificationMessage(String message) {
        this(message, null);
    }

    public NotificationMessage(String message, Boolean gameOver) {
        super(ServerMessageType.NOTIFICATION);
        this.message = message;
        this.gameOver = gameOver;
    }

    public String getMessage() {
        return message;
    }

    public boolean isGameOver() {
        return Boolean.TRUE.equals(gameOver);
    }
}
