package model;

import chess.ChessGame;

/**
 * Stores the identifying information, players, and current state of a chess game.
 */
public record GameData(
        int gameID,
        String whiteUsername,
        String blackUsername,
        String gameName,
        ChessGame game) {
}
