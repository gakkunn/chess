package ui;

import client.GameSummary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class GameListRendererTests {

    private final GameListRenderer renderer = new GameListRenderer();

    @Test
    void renderReportsWhenThereAreNoGames() {
        String output = renderer.render(List.of());

        Assertions.assertEquals("No games are available.", output);
    }

    @Test
    void renderDisplaysNumberedGamesAndPlayerNames() {
        List<GameSummary> games = List.of(
                new GameSummary(42, null, "black-player", "First Match"),
                new GameSummary(87, "white-player", null, "Second Match"));

        String output = renderer.render(games);
        String expected = String.join(
                System.lineSeparator(),
                "Available games:",
                "  1. First Match | White: Open | Black: black-player",
                "  2. Second Match | White: white-player | Black: Open");

        Assertions.assertAll(
                () -> Assertions.assertEquals(expected, output),
                () -> Assertions.assertFalse(output.contains("42")),
                () -> Assertions.assertFalse(output.contains("87")));
    }
}
