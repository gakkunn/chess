package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import ui.BoardRenderer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class GameBoardView {

    private final BoardRenderer renderer = new BoardRenderer();

    String render(ChessGame game, ChessGame.TeamColor perspective) {
        return renderer.render(game.getBoard(), perspective);
    }

    String evaluate(String command, String[] tokens, ChessGame game, ChessGame.TeamColor perspective) {
        if (command.equals("redraw")) {
            return tokens.length == 1 ? render(game, perspective) : "Usage: redraw";
        }
        if (tokens.length != 2) {
            return "Usage: highlight <square>";
        }
        ChessPosition selected;
        try {
            selected = MoveParser.parsePosition(tokens[1]);
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
        String square = tokens[1].toLowerCase(Locale.ROOT);
        if (game.getBoard().getPiece(selected) == null) {
            return "There is no piece at " + square + ".";
        }
        Set<ChessPosition> destinations = new HashSet<>();
        for (ChessMove move : game.validMoves(selected)) {
            destinations.add(move.getEndPosition());
        }
        String description = "Selected " + square + " (yellow). "
                + (destinations.isEmpty() ? "No legal moves." : "Legal moves are green.");
        return description + System.lineSeparator()
                + renderer.renderHighlighted(game.getBoard(), perspective, selected, destinations);
    }
}
