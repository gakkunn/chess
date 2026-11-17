package websocket;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;
import chess.InvalidMoveException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import websocket.commands.MakeMoveCommand;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.util.Set;
import java.util.stream.Stream;

public class WebSocketMessageCodecTests {

    private final WebSocketMessageCodec codec = new WebSocketMessageCodec();

    @ParameterizedTest
    @EnumSource(value = UserGameCommand.CommandType.class,
            names = {"CONNECT", "LEAVE", "RESIGN"})
    public void simpleCommandsPreserveRequiredFields(UserGameCommand.CommandType type) {
        UserGameCommand original = new UserGameCommand(type, "token-123", 27);

        String json = codec.serialize(original);
        JsonObject object = parse(json);
        UserGameCommand restored = codec.deserializeCommand(json);

        Assertions.assertEquals(Set.of("commandType", "authToken", "gameID"), object.keySet());
        Assertions.assertEquals(type.name(), object.get("commandType").getAsString());
        Assertions.assertEquals("token-123", object.get("authToken").getAsString());
        Assertions.assertEquals(27, object.get("gameID").getAsInt());
        Assertions.assertEquals(original, restored);
    }

    @Test
    public void moveCommandUsesRequiredJsonFieldsThroughBaseType() {
        UserGameCommand command = new MakeMoveCommand(
                "token-123", 27, new ChessMove(position(2, 5), position(4, 5), null));
        String expectedJson = """
                {
                    "commandType": "MAKE_MOVE",
                    "authToken": "token-123",
                    "gameID": 27,
                    "move": {
                        "startPosition": {"row": 2, "column": 5},
                        "endPosition": {"row": 4, "column": 5}
                    }
                }
                """;

        Assertions.assertEquals(parse(expectedJson), parse(codec.serialize(command)));

        MakeMoveCommand restored = Assertions.assertInstanceOf(
                MakeMoveCommand.class, codec.deserializeCommand(expectedJson));
        Assertions.assertEquals(UserGameCommand.CommandType.MAKE_MOVE, restored.getCommandType());
        Assertions.assertEquals("token-123", restored.getAuthToken());
        Assertions.assertEquals(27, restored.getGameID());
        Assertions.assertEquals(
                new ChessMove(position(2, 5), position(4, 5), null), restored.getMove());
    }

    @ParameterizedTest
    @EnumSource(value = ChessPiece.PieceType.class,
            names = {"QUEEN", "ROOK", "BISHOP", "KNIGHT"})
    public void promotionSurvivesMoveAndBoardMessages(ChessPiece.PieceType promotion)
            throws InvalidMoveException {
        UserGameCommand command = new MakeMoveCommand(
                "promotion-token", 28,
                new ChessMove(position(7, 3), position(8, 3), promotion));

        String json = codec.serialize(command);
        Assertions.assertEquals(promotion.name(),
                parse(json).getAsJsonObject("move").get("promotionPiece").getAsString());
        MakeMoveCommand restored = Assertions.assertInstanceOf(
                MakeMoveCommand.class, codec.deserializeCommand(json));
        ChessGame game = promotionGame();
        game.makeMove(restored.getMove());

        LoadGameMessage received = Assertions.assertInstanceOf(
                LoadGameMessage.class,
                codec.deserializeMessage(codec.serialize(new LoadGameMessage(game))));
        Assertions.assertEquals(game, received.getGame());
        Assertions.assertEquals(
                new ChessPiece(ChessGame.TeamColor.WHITE, promotion),
                received.getGame().getBoard().getPiece(position(8, 3)));
        Assertions.assertNull(received.getGame().getBoard().getPiece(position(7, 3)));
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, received.getGame().getTeamTurn());
    }

    @Test
    public void loadGamePreservesCurrentBoardAndAllowsPlayToContinue()
            throws InvalidMoveException {
        ChessGame game = new ChessGame();
        game.makeMove(new ChessMove(position(2, 5), position(4, 5), null));
        ServerMessage message = new LoadGameMessage(game);

        String json = codec.serialize(message);
        JsonObject object = parse(json);
        Assertions.assertEquals(Set.of("serverMessageType", "game"), object.keySet());
        Assertions.assertEquals("LOAD_GAME", object.get("serverMessageType").getAsString());
        LoadGameMessage restored = Assertions.assertInstanceOf(
                LoadGameMessage.class, codec.deserializeMessage(json));
        Assertions.assertEquals(game, restored.getGame());
        Assertions.assertNotSame(game, restored.getGame());
        Assertions.assertNotSame(game.getBoard(), restored.getGame().getBoard());

        restored.getGame().makeMove(new ChessMove(position(7, 5), position(5, 5), null));
        Assertions.assertEquals(ChessGame.TeamColor.WHITE, restored.getGame().getTeamTurn());
        Assertions.assertNull(restored.getGame().getBoard().getPiece(position(7, 5)));
        Assertions.assertEquals(
                new ChessPiece(ChessGame.TeamColor.BLACK, ChessPiece.PieceType.PAWN),
                restored.getGame().getBoard().getPiece(position(5, 5)));
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, game.getTeamTurn());
    }

    @Test
    public void loadGamePreservesCompletionAndPreventsFurtherMoves()
            throws InvalidMoveException {
        ChessGame game = new ChessGame();
        game.makeMove(new ChessMove(position(2, 5), position(4, 5), null));
        game.endGame();

        String json = codec.serialize(new LoadGameMessage(game));
        LoadGameMessage restored = Assertions.assertInstanceOf(
                LoadGameMessage.class, codec.deserializeMessage(json));

        Assertions.assertTrue(parse(json).getAsJsonObject("game").get("gameOver").getAsBoolean());
        Assertions.assertTrue(restored.getGame().isGameOver());
        Assertions.assertEquals(game, restored.getGame());
        Assertions.assertThrows(InvalidMoveException.class,
                () -> restored.getGame().makeMove(
                        new ChessMove(position(7, 5), position(5, 5), null)));
        Assertions.assertEquals(game, restored.getGame());
    }

    @Test
    public void errorUsesRequiredFieldAndIncludesErrorPrefix() {
        ServerMessage message = new ErrorMessage("The requested move is not legal.");

        String json = codec.serialize(message);
        JsonObject object = parse(json);
        Assertions.assertEquals(Set.of("serverMessageType", "errorMessage"), object.keySet());
        Assertions.assertEquals("ERROR", object.get("serverMessageType").getAsString());
        Assertions.assertEquals("Error: The requested move is not legal.",
                object.get("errorMessage").getAsString());
        ErrorMessage restored = Assertions.assertInstanceOf(
                ErrorMessage.class, codec.deserializeMessage(json));
        Assertions.assertEquals("Error: The requested move is not legal.", restored.getErrorMessage());
    }

    @Test
    public void existingErrorPrefixIsPreserved() {
        String json = codec.serialize(new ErrorMessage("Error: unauthorized"));

        ErrorMessage restored = Assertions.assertInstanceOf(
                ErrorMessage.class, codec.deserializeMessage(json));

        Assertions.assertEquals("Error: unauthorized", restored.getErrorMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    public void missingErrorDescriptionStillProducesAnError(String description) {
        String json = codec.serialize(new ErrorMessage(description));

        ErrorMessage restored = Assertions.assertInstanceOf(
                ErrorMessage.class, codec.deserializeMessage(json));

        Assertions.assertEquals("Error: unable to complete the request.", restored.getErrorMessage());
    }

    @Test
    public void notificationPreservesUnicodeQuotesAndLineBreaks() {
        String text = "太郎 moved \"e2\" to e4.\nBlack is next.";
        ServerMessage message = new NotificationMessage(text);

        String json = codec.serialize(message);
        JsonObject object = parse(json);
        Assertions.assertEquals(Set.of("serverMessageType", "message"), object.keySet());
        Assertions.assertEquals("NOTIFICATION", object.get("serverMessageType").getAsString());
        Assertions.assertEquals(text, object.get("message").getAsString());
        NotificationMessage restored = Assertions.assertInstanceOf(
                NotificationMessage.class, codec.deserializeMessage(json));
        Assertions.assertEquals(text, restored.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "null", "[]", "17", "\"CONNECT\"", "{", "{}"})
    public void nonObjectOrMissingTypeIsRejected(String json) {
        Assertions.assertThrows(JsonParseException.class, () -> codec.deserializeCommand(json));
        Assertions.assertThrows(JsonParseException.class, () -> codec.deserializeMessage(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"UNKNOWN\"", "17", "{}", "[]", "\"\""})
    public void invalidTypeFieldsAreRejected(String typeJson) {
        Assertions.assertThrows(JsonParseException.class,
                () -> codec.deserializeCommand("{\"commandType\":" + typeJson + "}"));
        Assertions.assertThrows(JsonParseException.class,
                () -> codec.deserializeMessage("{\"serverMessageType\":" + typeJson + "}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"move\":null}", "{\"move\":\"e2e4\"}",
            "{\"move\":{}}", "{\"move\":{\"startPosition\":{\"row\":2,\"column\":5}}}"})
    public void moveCommandRequiresMovePositions(String payloadJson) {
        JsonObject object = parse(payloadJson);
        object.addProperty("commandType", "MAKE_MOVE");

        Assertions.assertThrows(JsonParseException.class,
                () -> codec.deserializeCommand(object.toString()));
    }

    @ParameterizedTest
    @MethodSource("invalidMessagePayloads")
    public void serverMessagesRequireTheirPayload(String json) {
        Assertions.assertThrows(JsonParseException.class, () -> codec.deserializeMessage(json));
    }

    private static Stream<String> invalidMessagePayloads() {
        return Stream.of(
                "{\"serverMessageType\":\"LOAD_GAME\"}",
                "{\"serverMessageType\":\"LOAD_GAME\",\"game\":null}",
                "{\"serverMessageType\":\"LOAD_GAME\",\"game\":\"board\"}",
                "{\"serverMessageType\":\"LOAD_GAME\",\"game\":{}}",
                "{\"serverMessageType\":\"LOAD_GAME\",\"game\":{\"teamTurn\":\"WHITE\",\"board\":{}}}",
                "{\"serverMessageType\":\"ERROR\"}",
                "{\"serverMessageType\":\"ERROR\",\"errorMessage\":null}",
                "{\"serverMessageType\":\"ERROR\",\"errorMessage\":17}",
                "{\"serverMessageType\":\"NOTIFICATION\"}",
                "{\"serverMessageType\":\"NOTIFICATION\",\"message\":null}",
                "{\"serverMessageType\":\"NOTIFICATION\",\"message\":\"\"}");
    }

    private JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private ChessPosition position(int row, int column) {
        return new ChessPosition(row, column);
    }

    private ChessGame promotionGame() {
        ChessBoard board = new ChessBoard();
        board.addPiece(position(1, 5),
                new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KING));
        board.addPiece(position(8, 8),
                new ChessPiece(ChessGame.TeamColor.BLACK, ChessPiece.PieceType.KING));
        board.addPiece(position(7, 3),
                new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.PAWN));

        ChessGame game = new ChessGame();
        game.setBoard(board);
        return game;
    }
}
