package service.result;

import chess.ChessGame;
import model.GameData;

public record ConnectGameResult(
        String username, GameData game, ChessGame.TeamColor playerColor) {
}
