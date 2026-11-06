package ui;

import client.GameSummary;

import java.util.List;
import java.util.Objects;

public class GameListRenderer {

    public String render(List<GameSummary> games) {
        Objects.requireNonNull(games, "Game list cannot be null.");

        if (games.isEmpty()) {
            return "No games are available.";
        }

        StringBuilder output = new StringBuilder("Available games:");

        for (int index = 0; index < games.size(); index++) {
            GameSummary game = games.get(index);
            output.append(System.lineSeparator())
                    .append("  ")
                    .append(index + 1)
                    .append(". ")
                    .append(game.gameName())
                    .append(" | White: ")
                    .append(playerName(game.whiteUsername()))
                    .append(" | Black: ")
                    .append(playerName(game.blackUsername()));
        }

        return output.toString();
    }

    private String playerName(String username) {
        if (username == null || username.isBlank()) {
            return "Open";
        }

        return username;
    }
}
