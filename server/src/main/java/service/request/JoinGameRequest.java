package service.request;

import chess.ChessGame;

/**
 * Contains the authenticated session, requested color, and game to join.
 */
public record JoinGameRequest(
        String authToken,
        ChessGame.TeamColor playerColor,
        Integer gameID) {
}
