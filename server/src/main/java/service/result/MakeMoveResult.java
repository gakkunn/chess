package service.result;

import model.GameData;

public record MakeMoveResult(String username, GameData game) {
}
