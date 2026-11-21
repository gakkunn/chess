package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import model.AuthData;
import ui.BoardRenderer;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class GameplaySession {

    private static final String RECONNECT_HINT = "Type 'reconnect' to return to the game, or 'list' to choose a game.";
    private final ServerFacade facade;
    private final Consumer<String> output;
    private final Consumer<String> expiredSession;
    private final Duration loadTimeout;
    private final BoardRenderer renderer = new BoardRenderer();
    private final Object lock = new Object();
    private Connection active;
    private Target resumable;

    GameplaySession(ServerFacade facade, Consumer<String> output, Consumer<String> expiredSession, Duration loadTimeout) {
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
        return switch (command) {
            case "redraw" -> redraw(tokens);
            case "move" -> makeMove(tokens);
            default -> "Unknown command. Type 'help' to see available commands.";
        };
    }

    String help() {
        synchronized (lock) {
            String moveHelp = active != null && active.target.color != null
                    ? "  move <from> <to> [" + MoveParser.PROMOTION_CHOICES + "] - Make a move"
                    + System.lineSeparator()
                    + "    Use squares a1-h8, e.g. move e2 e4. Choose a piece when promoting a pawn."
                    + System.lineSeparator() : "";
            return "Available commands:" + System.lineSeparator()
                    + "  redraw - Redraw the current chess board" + System.lineSeparator()
                    + moveHelp
                    + "  help - Show available commands" + System.lineSeparator()
                    + "  quit - Exit the chess client";
        }
    }

    private String redraw(String[] tokens) {
        if (tokens.length != 1) {
            return "Usage: redraw";
        }
        synchronized (lock) {
            if (!hasBoard()) {
                return "No game board is available. " + RECONNECT_HINT;
            }
            return renderer.render(active.game.getBoard(), active.target.perspective());
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
                return "No game board is available. " + RECONNECT_HINT;
            }
            connection = active;
            String error = validateMove(connection, move, tokens);
            if (error != null) {
                return error;
            }
        }
        try {
            Target target = connection.target;
            facade.makeMove(target.auth.authToken(), target.game.gameID(), move);
            return "Move submitted.";
        } catch (ServerFacadeException exception) {
            return finishFailure(connection, false) ? exception.getMessage() + " " + RECONNECT_HINT : "";
        }
    }

    private boolean hasBoard() {
        return active != null && !active.closing && active.game != null;
    }

    private String validateMove(Connection connection, ChessMove move, String[] tokens) {
        ChessGame game = connection.game;
        ChessGame.TeamColor color = connection.target.color;
        if (color == null) {
            return "Observers cannot make moves.";
        }
        if (game.isGameOver()) {
            return "The game is over. You can still redraw the board.";
        }
        if (game.getTeamTurn() != color) {
            return "It is not your turn.";
        }
        ChessPiece piece = game.getBoard().getPiece(move.getStartPosition());
        if (piece == null || piece.getTeamColor() != color) {
            return "Choose one of your own pieces.";
        }
        boolean promotes = piece.getPieceType() == ChessPiece.PieceType.PAWN
                && move.getEndPosition().getRow() == (color == ChessGame.TeamColor.WHITE ? 8 : 1);
        if (promotes && move.getPromotionPiece() == null) {
            return "Choose a promotion piece: move " + tokens[1] + " " + tokens[2]
                    + " <" + MoveParser.PROMOTION_CHOICES + ">.";
        }
        if (!promotes && move.getPromotionPiece() != null) {
            return "Only a pawn reaching the last rank can promote.";
        }
        return null;
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
            return finishFailure(connection, false) ? exception.getMessage() + " " + RECONNECT_HINT : "";
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
                boolean firstBoard = connection.game == null;
                connection.game = new ChessGame(load.getGame());
                connection.loaded.complete(null);
                Target target = connection.target;
                text = (firstBoard ? target.description() + System.lineSeparator() : "")
                        + renderer.render(connection.game.getBoard(), target.perspective());
            } else if (message instanceof NotificationMessage notification) {
                text = notification.getMessage();
            } else {
                return;
            }
        }
        output.accept(text);
    }

    private void receiveError(Connection connection, String message) {
        boolean initial;
        synchronized (lock) {
            if (active != connection || connection.closing) {
                return;
            }
            initial = connection.game == null;
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

    private boolean finishFailure(Connection connection, boolean unauthorized) {
        return finishFailure(connection, unauthorized, false);
    }

    private boolean finishFailure(Connection connection, boolean unauthorized, boolean onlyLoading) {
        synchronized (lock) {
            if (active != connection || connection.closing || (onlyLoading && connection.game != null)) {
                return false;
            }
            connection.closing = true;
            connection.loaded.complete(null);
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
                active.closing = true;
                active.loaded.complete(null);
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

        private Connection(Target target) {
            this.target = target;
        }
    }
}
