package client;

import chess.ChessGame;
import chess.ChessMove;
import model.AuthData;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class GameplaySession {

    private static final String RECONNECT_HINT = "Type 'reconnect' to return to the game, or 'list' to choose a game.";
    private static final String NO_BOARD = "No game board is available. " + RECONNECT_HINT;
    private static final String CONFIRM_RESIGN = "Resign this game? Type 'yes' to confirm or 'no' to cancel.";
    private static final Set<String> CONFIRMATION_COMMANDS = Set.of("yes", "no", "redraw", "highlight", "leave");
    private static final String BOARD_HELP = String.join(System.lineSeparator(),
            "Available commands:",
            "  redraw - Redraw the current chess board",
            "  highlight <square> - Highlight a piece and its legal moves");
    private static final String PLAYER_HELP = String.join(System.lineSeparator(),
            "  move <from> <to> [" + MoveParser.PROMOTION_CHOICES + "] - Make a move",
            "    Use squares a1-h8, e.g. move e2 e4. Choose a piece when promoting a pawn.",
            "  resign - Forfeit the game after confirmation");
    private static final String SESSION_HELP = String.join(System.lineSeparator(),
            "  leave - Leave the game",
            "  help - Show available commands",
            "  quit - Exit the chess client");

    private final ServerFacade facade;
    private final Consumer<String> output;
    private final Consumer<String> expiredSession;
    private final Duration loadTimeout;
    private final GameBoardView boardView = new GameBoardView();
    private final Object lock = new Object();
    private Connection active;
    private Target resumable;

    GameplaySession(ServerFacade facade, Consumer<String> output, Consumer<String> expiredSession,
                    Duration loadTimeout) {
        this.facade = facade;
        this.output = output;
        this.expiredSession = expiredSession;
        this.loadTimeout = loadTimeout;
    }

    ClientState getState() {
        synchronized (lock) {
            if (active == null) {
                return ClientState.SIGNED_IN;
            }
            return active.game == null || active.closing ? ClientState.CONNECTING : ClientState.IN_GAME;
        }
    }

    String evaluate(String command, String[] tokens) {
        synchronized (lock) {
            if (active != null && active.resignation == ResignationState.CONFIRMING
                    && !CONFIRMATION_COMMANDS.contains(command)) {
                return CONFIRM_RESIGN;
            }
        }
        return switch (command) {
            case "redraw", "highlight" -> displayBoard(command, tokens);
            case "move" -> makeMove(tokens);
            case "leave" -> tokens.length == 1 ? depart(false) : "Usage: leave";
            case "resign" -> resign(tokens);
            case "yes", "no" -> confirmResignation(command.equals("yes"), tokens);
            default -> "Unknown command. Type 'help' to see available commands.";
        };
    }

    String help() {
        synchronized (lock) {
            String playerCommands = active != null && active.target.color != null
                    ? PLAYER_HELP + System.lineSeparator() : "";
            String confirmation = active != null && active.resignation == ResignationState.CONFIRMING
                    ? System.lineSeparator() + CONFIRM_RESIGN : "";
            return BOARD_HELP + System.lineSeparator() + playerCommands + SESSION_HELP + confirmation;
        }
    }

    private String displayBoard(String command, String[] tokens) {
        synchronized (lock) {
            if (!hasBoard()) {
                return NO_BOARD;
            }
            return boardView.evaluate(command, tokens, active.game, active.target.perspective());
        }
    }

    private String makeMove(String[] tokens) {
        ChessMove move;
        try {
            move = MoveParser.parse(tokens);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
        Connection connection;
        synchronized (lock) {
            if (!hasBoard()) {
                return NO_BOARD;
            }
            connection = active;
            if (connection.resignation == ResignationState.SUBMITTED) {
                return "Waiting for the server to confirm resignation.";
            }
            String error = MoveValidator.validate(connection.game, connection.target.color, move);
            if (error != null) {
                return error;
            }
        }
        try {
            Target target = connection.target;
            facade.makeMove(target.auth.authToken(), target.game.gameID(), move);
            return "Move submitted.";
        } catch (ServerFacadeException exception) {
            return connectionFailure(connection, exception);
        }
    }

    private boolean hasBoard() {
        return active != null && !active.closing && active.game != null;
    }

    private String resign(String[] tokens) {
        if (tokens.length != 1) {
            return "Usage: resign";
        }
        synchronized (lock) {
            if (!hasBoard()) {
                return NO_BOARD;
            }
            if (active.target.color == null) {
                return "Observers cannot resign.";
            }
            if (active.game.isGameOver()) {
                return "The game is already over.";
            }
            if (active.resignation == ResignationState.SUBMITTED) {
                return "Waiting for the server to confirm resignation.";
            }
            active.resignation = ResignationState.CONFIRMING;
            return CONFIRM_RESIGN;
        }
    }

    private String confirmResignation(boolean confirmed, String[] tokens) {
        Connection connection;
        synchronized (lock) {
            if (!hasBoard() || active.resignation != ResignationState.CONFIRMING) {
                return "There is no resignation to confirm. Type 'resign' first.";
            }
            if (tokens.length != 1) {
                return "Please enter only 'yes' or 'no'.";
            }
            connection = active;
            connection.resignation = ResignationState.NONE;
            if (!confirmed) {
                return "Resignation cancelled.";
            }
            connection.resignation = ResignationState.SUBMITTED;
        }
        try {
            Target target = connection.target;
            facade.resignGame(target.auth.authToken(), target.game.gameID());
            return "Resignation submitted.";
        } catch (ServerFacadeException exception) {
            return connectionFailure(connection, exception);
        }
    }

    String close() {
        return depart(true);
    }

    private String depart(boolean quitting) {
        Connection connection;
        synchronized (lock) {
            connection = active != null && !active.closing ? active : null;
            if (connection != null) {
                connection.beginClosing();
            } else if (!quitting) {
                return "There is no active game to leave.";
            }
        }
        String error = null;
        try {
            if (connection != null) {
                Target target = connection.target;
                facade.leaveGame(target.auth.authToken(), target.game.gameID());
            }
        } catch (ServerFacadeException exception) {
            error = exception.getMessage();
        } finally {
            facade.disconnect();
            synchronized (lock) {
                active = null;
                if (quitting || error == null) {
                    resumable = null;
                }
            }
        }
        if (error != null) {
            return "Could not notify the server that you left: " + error + (quitting ? "" : " " + RECONNECT_HINT);
        }
        return quitting ? "" : "Left '" + connection.target.game.gameName() + "'.";
    }

    String connect(AuthData auth, GameSummary game, ChessGame.TeamColor color) {
        return connect(new Target(auth, game, color));
    }

    String reconnect() {
        Target target;
        synchronized (lock) {
            target = resumable;
        }
        return target == null ? "There is no game to reconnect to. Type 'list' to choose a game." : connect(target);
    }

    private String connect(Target target) {
        Connection connection = new Connection(target);
        synchronized (lock) {
            if (active != null) {
                return "A game connection is already open or connecting.";
            }
            active = connection;
            resumable = target;
        }
        try {
            facade.connectGame(target.auth.authToken(), target.game.gameID(), new ServerMessageObserver() {
                @Override
                public void notify(ServerMessage message) {
                    receive(connection, message);
                }

                @Override
                public void onDisconnect() {
                    fail(connection, "The game connection was interrupted. " + RECONNECT_HINT, false);
                }
            });
        } catch (ServerFacadeException exception) {
            return connectionFailure(connection, exception);
        }
        connection.loaded.orTimeout(loadTimeout.toMillis(), TimeUnit.MILLISECONDS).exceptionallyAsync(exception -> {
            if (finishFailure(connection, false, true)) {
                output.accept("The server did not send the game board in time. " + RECONNECT_HINT);
            }
            return null;
        });
        return "Connecting to '" + target.game.gameName() + "'...";
    }

    private void receive(Connection connection, ServerMessage message) {
        if (message instanceof ErrorMessage error) {
            receiveError(connection, error.getErrorMessage());
            return;
        }
        String text;
        synchronized (lock) {
            if (active != connection || connection.closing) {
                return;
            }
            if (message instanceof LoadGameMessage load) {
                text = loadBoard(connection, load);
            } else if (message instanceof NotificationMessage notification) {
                text = applyNotification(connection, notification);
            } else {
                return;
            }
        }
        output.accept(text);
    }

    private String loadBoard(Connection connection, LoadGameMessage load) {
        boolean firstBoard = connection.game == null;
        connection.game = new ChessGame(load.getGame());
        if (connection.game.isGameOver()) {
            connection.resignation = ResignationState.NONE;
        }
        connection.loaded.complete(null);
        Target target = connection.target;
        return (firstBoard ? target.description() + System.lineSeparator() : "")
                + boardView.render(connection.game, target.perspective());
    }

    private String applyNotification(Connection connection, NotificationMessage notification) {
        if (notification.isGameOver() && connection.game != null) {
            connection.game.endGame();
            connection.resignation = ResignationState.NONE;
        }
        return notification.getMessage();
    }

    private void receiveError(Connection connection, String message) {
        boolean initial;
        synchronized (lock) {
            if (active != connection || connection.closing) {
                return;
            }
            initial = connection.game == null;
            connection.resignation = ResignationState.NONE;
        }
        boolean unauthorized = "Error: unauthorized".equals(message);
        if (initial || unauthorized) {
            fail(connection, unauthorized ? "Your session is no longer valid. Please sign in again."
                    : message + " " + RECONNECT_HINT, unauthorized);
        } else {
            output.accept(message);
        }
    }

    private void fail(Connection connection, String message, boolean unauthorized) {
        if (finishFailure(connection, unauthorized)) {
            if (unauthorized) {
                expiredSession.accept(connection.target.auth.authToken());
            }
            output.accept(message);
        }
    }

    private String connectionFailure(Connection connection, ServerFacadeException exception) {
        return finishFailure(connection, false) ? exception.getMessage() + " " + RECONNECT_HINT : "";
    }

    private boolean finishFailure(Connection connection, boolean unauthorized) {
        return finishFailure(connection, unauthorized, false);
    }

    /**
     * Ends a failed connection unless it was already replaced, closed, or (when onlyLoading) loaded.
     *
     * @return true if this call ended the connection and the caller should report the failure
     */
    private boolean finishFailure(Connection connection, boolean unauthorized, boolean onlyLoading) {
        synchronized (lock) {
            if (active != connection || connection.closing || (onlyLoading && connection.game != null)) {
                return false;
            }
            connection.beginClosing();
            if (unauthorized) {
                resumable = null;
            }
        }
        // Disconnect outside the state lock: the transport may be delivering a callback.
        facade.disconnect();
        synchronized (lock) {
            if (active != connection) {
                return false;
            }
            active = null;
        }
        return true;
    }

    void reset() {
        synchronized (lock) {
            if (active != null) {
                active.beginClosing();
            }
            active = null;
            resumable = null;
        }
        facade.disconnect();
    }

    private record Target(AuthData auth, GameSummary game, ChessGame.TeamColor color) {
        ChessGame.TeamColor perspective() {
            return color == null ? ChessGame.TeamColor.WHITE : color;
        }

        String description() {
            return color == null ? "Observing '" + game.gameName() + "'."
                    : "Joined '" + game.gameName() + "' as " + color + ".";
        }
    }

    private static final class Connection {
        private final Target target;
        private final CompletableFuture<Void> loaded = new CompletableFuture<>();
        private ChessGame game;
        private boolean closing;
        private ResignationState resignation = ResignationState.NONE;

        private Connection(Target target) {
            this.target = target;
        }

        private void beginClosing() {
            closing = true;
            loaded.complete(null);
        }
    }

    private enum ResignationState {
        NONE,
        CONFIRMING,
        SUBMITTED
    }
}
