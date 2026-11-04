package ui;

import chess.ChessBoard;
import chess.ChessGame;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

public class BoardRendererTests {

    private static final Pattern ANSI_SEQUENCE =
            Pattern.compile("\u001B\\[[;\\d]*m");

    private BoardRenderer renderer;
    private ChessBoard board;

    @BeforeEach
    void setUp() {
        renderer = new BoardRenderer();
        board = new ChessGame().getBoard();
    }

    @Test
    void renderWhiteDisplaysCoordinatesInWhiteOrientation() {
        String[] lines = plainText(renderer.renderWhite(board)).split("\\R");
        String expectedColumns = "a  b  c  d  e  f  g  h";

        Assertions.assertAll(
                () -> Assertions.assertEquals(10, lines.length),
                () -> Assertions.assertEquals(
                        expectedColumns,
                        lines[0].strip()),
                () -> Assertions.assertEquals(
                        expectedColumns,
                        lines[9].strip()),
                () -> Assertions.assertTrue(lines[1].strip().startsWith("8")),
                () -> Assertions.assertTrue(lines[8].strip().startsWith("1")));
    }

    @Test
    void renderWhiteDisplaysInitialPiecesInCorrectOrder() {
        String[] lines = plainText(renderer.renderWhite(board)).split("\\R");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "8  ♜  ♞  ♝  ♛  ♚  ♝  ♞  ♜  8",
                        lines[1].strip()),
                () -> Assertions.assertEquals(
                        "7  ♟  ♟  ♟  ♟  ♟  ♟  ♟  ♟  7",
                        lines[2].strip()),
                () -> Assertions.assertEquals(
                        "2  ♙  ♙  ♙  ♙  ♙  ♙  ♙  ♙  2",
                        lines[7].strip()),
                () -> Assertions.assertEquals(
                        "1  ♖  ♘  ♗  ♕  ♔  ♗  ♘  ♖  1",
                        lines[8].strip()));
    }

    @Test
    void renderWhiteUsesCorrectAlternatingSquareColors() {
        String renderedBoard = renderer.renderWhite(board);
        String[] lines = renderedBoard.split("\\R");
        int lightSquares = countOccurrences(
                renderedBoard,
                EscapeSequences.SET_BG_COLOR_WHITE);
        int darkSquares = countOccurrences(
                renderedBoard,
                EscapeSequences.SET_BG_COLOR_DARK_GREEN);

        Assertions.assertAll(
                () -> Assertions.assertEquals(32, lightSquares),
                () -> Assertions.assertEquals(32, darkSquares),
                () -> Assertions.assertTrue(
                        lines[1].indexOf(EscapeSequences.SET_BG_COLOR_WHITE)
                                < lines[1].indexOf(
                                EscapeSequences.SET_BG_COLOR_DARK_GREEN)),
                () -> Assertions.assertTrue(
                        lines[8].indexOf(EscapeSequences.SET_BG_COLOR_DARK_GREEN)
                                < lines[8].indexOf(
                                EscapeSequences.SET_BG_COLOR_WHITE)));
    }

    @Test
    void renderWhiteUsesPieceColorsAndResetsTerminalColors() {
        String renderedBoard = renderer.renderWhite(board);
        String expectedEnding = EscapeSequences.RESET_TEXT_COLOR
                + EscapeSequences.RESET_BG_COLOR
                + System.lineSeparator();

        Assertions.assertAll(
                () -> Assertions.assertTrue(renderedBoard.contains(
                        EscapeSequences.SET_TEXT_COLOR_BLUE
                                + EscapeSequences.BLACK_KING)),
                () -> Assertions.assertTrue(renderedBoard.contains(
                        EscapeSequences.SET_TEXT_COLOR_RED
                                + EscapeSequences.WHITE_KING)),
                () -> Assertions.assertTrue(renderedBoard.endsWith(expectedEnding)));
    }

    @Test
    void renderBlackDisplaysCoordinatesInBlackOrientation() {
        String[] lines = plainText(renderer.render(
                board,
                ChessGame.TeamColor.BLACK)).split("\\R");
        String expectedColumns = "h  g  f  e  d  c  b  a";

        Assertions.assertAll(
                () -> Assertions.assertEquals(10, lines.length),
                () -> Assertions.assertEquals(
                        expectedColumns,
                        lines[0].strip()),
                () -> Assertions.assertEquals(
                        expectedColumns,
                        lines[9].strip()),
                () -> Assertions.assertTrue(lines[1].strip().startsWith("1")),
                () -> Assertions.assertTrue(lines[8].strip().startsWith("8")));
    }

    @Test
    void renderBlackDisplaysInitialPiecesInCorrectOrder() {
        String[] lines = plainText(renderer.render(
                board,
                ChessGame.TeamColor.BLACK)).split("\\R");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "1  ♖  ♘  ♗  ♔  ♕  ♗  ♘  ♖  1",
                        lines[1].strip()),
                () -> Assertions.assertEquals(
                        "2  ♙  ♙  ♙  ♙  ♙  ♙  ♙  ♙  2",
                        lines[2].strip()),
                () -> Assertions.assertEquals(
                        "7  ♟  ♟  ♟  ♟  ♟  ♟  ♟  ♟  7",
                        lines[7].strip()),
                () -> Assertions.assertEquals(
                        "8  ♜  ♞  ♝  ♚  ♛  ♝  ♞  ♜  8",
                        lines[8].strip()));
    }

    @Test
    void renderBlackUsesCorrectAlternatingSquareColors() {
        String renderedBoard = renderer.render(
                board,
                ChessGame.TeamColor.BLACK);
        String[] lines = renderedBoard.split("\\R");
        int lightSquares = countOccurrences(
                renderedBoard,
                EscapeSequences.SET_BG_COLOR_WHITE);
        int darkSquares = countOccurrences(
                renderedBoard,
                EscapeSequences.SET_BG_COLOR_DARK_GREEN);

        Assertions.assertAll(
                () -> Assertions.assertEquals(32, lightSquares),
                () -> Assertions.assertEquals(32, darkSquares),
                () -> Assertions.assertTrue(
                        lines[1].indexOf(EscapeSequences.SET_BG_COLOR_WHITE)
                                < lines[1].indexOf(
                                EscapeSequences.SET_BG_COLOR_DARK_GREEN)),
                () -> Assertions.assertTrue(
                        lines[8].indexOf(EscapeSequences.SET_BG_COLOR_DARK_GREEN)
                                < lines[8].indexOf(
                                EscapeSequences.SET_BG_COLOR_WHITE)));
    }

    private String plainText(String renderedBoard) {
        return ANSI_SEQUENCE.matcher(renderedBoard).replaceAll("");
    }

    private int countOccurrences(String text, String expected) {
        int count = 0;
        int position = 0;

        while ((position = text.indexOf(expected, position)) >= 0) {
            count++;
            position += expected.length();
        }

        return count;
    }
}
