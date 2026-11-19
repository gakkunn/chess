package server.websocket;

import chess.ChessBoard;
import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPiece;
import chess.ChessPosition;
import dataaccess.MemoryGameDAO;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import websocket.commands.MakeMoveCommand;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class WebSocketMoveTests extends WebSocketEndpointTestSupport {

    private ControlledGameDAO controlledGames;
    private SocketClient white;
    private SocketClient black;
    private SocketClient observer;

    @Override
    protected MemoryGameDAO createGameDAO() {
        controlledGames = new ControlledGameDAO();
        return controlledGames;
    }

    @BeforeEach
    public void connectParticipants() throws Exception {
        white = openSocket();
        black = openSocket();
        observer = openSocket();
        connect(white, "white-token", gameID);
        connect(black, "black-token", gameID);
        assertNotice(white, "black", "BLACK");
        connect(observer, "observer-token", gameID);
        assertNotice(white, "observer", "observer");
        assertNotice(black, "observer", "observer");
    }

    @Test
    public void legalMovesUpdateAllBoardsAndNotifyOnlyTheOtherParticipants() throws Exception {
        ChessMove whiteMove = move(2, 5, 4, 5);
        ChessGame expected = new ChessGame(gameDAO.getGame(gameID).game());
        expected.makeMove(whiteMove);

        white.send(new MakeMoveCommand("white-token", gameID, whiteMove));

        assertMoveDelivered(expected, white, "white", whiteMove);
        Assertions.assertEquals(expected, gameDAO.getGame(gameID).game());
        ChessMove blackMove = move(7, 5, 5, 5);
        expected.makeMove(blackMove);

        black.send(new MakeMoveCommand("black-token", gameID, blackMove));

        assertMoveDelivered(expected, black, "black", blackMove);
        GameData stored = gameDAO.getGame(gameID);
        Assertions.assertEquals(expected, stored.game());
        Assertions.assertEquals("white", stored.whiteUsername());
        Assertions.assertEquals("black", stored.blackUsername());
        Assertions.assertEquals("Test Game", stored.gameName());
    }

    @ParameterizedTest
    @MethodSource("invalidMoves")
    public void invalidMovesChangeNothingAndReturnAnErrorOnlyToTheSender(String token, ChessMove move)
            throws Exception {
        SocketClient sender = switch (token) {
            case "white-token" -> white;
            case "black-token" -> black;
            default -> observer;
        };
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());

        sender.send(new MakeMoveCommand(token, gameID, move));

        assertRejectedMove(sender, before);
    }

    @Test
    public void unknownPromotionCannotBeSilentlyTreatedAsANormalMove() throws Exception {
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());
        String json = """
                {"commandType":"MAKE_MOVE","authToken":"white-token","gameID":%d,
                 "move":{"startPosition":{"row":2,"column":5},
                         "endPosition":{"row":4,"column":5},"promotionPiece":"DRAGON"}}
                """.formatted(gameID);

        white.sendRaw(json);

        assertRejectedMove(white, before);
    }

    @Test
    public void aValidTokenDoesNotAllowAnUnconnectedSocketToMove() throws Exception {
        SocketClient unconnected = openSocket();
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());

        unconnected.send(new MakeMoveCommand("white-token", gameID, move(2, 5, 4, 5)));

        assertError(unconnected);
        assertAllQuiet();
        Assertions.assertEquals(before, gameDAO.getGame(gameID).game());
    }

    @Test
    public void aConnectedSocketCannotUseAnotherPlayersToken() throws Exception {
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());

        black.send(new MakeMoveCommand("white-token", gameID, move(2, 5, 4, 5)));

        assertRejectedMove(black, before);
    }

    @Test
    public void aConnectedSocketCannotMoveInAnotherGame() throws Exception {
        int otherGameID = gameDAO.createGame(new GameData(
                0, "white", "black", "Other Game", new ChessGame()));
        SocketClient otherObserver = openSocket();
        connect(otherObserver, "observer-token", otherGameID);
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());

        white.send(new MakeMoveCommand("white-token", otherGameID, move(2, 5, 4, 5)));

        assertRejectedMove(white, before);
        otherObserver.assertNoMessage();
        Assertions.assertEquals(new ChessGame(), gameDAO.getGame(otherGameID).game());
    }

    @Test
    public void revokedAuthenticationIsCheckedAgainForEachMove() throws Exception {
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());
        authDAO.deleteAuth("white-token");

        white.send(new MakeMoveCommand("white-token", gameID, move(2, 5, 4, 5)));

        assertRejectedMove(white, before);
    }

    @Test
    public void storedPlayerAssignmentIsCheckedAgainForEachMove() throws Exception {
        GameData stored = gameDAO.getGame(gameID);
        gameDAO.updateGame(new GameData(gameID, "replacement", stored.blackUsername(),
                stored.gameName(), stored.game()));
        ChessGame before = new ChessGame(stored.game());

        white.send(new MakeMoveCommand("white-token", gameID, move(2, 5, 4, 5)));

        assertRejectedMove(white, before);
        Assertions.assertEquals("replacement", gameDAO.getGame(gameID).whiteUsername());
    }

    @Test
    public void completedGamesRejectMovesWithoutBroadcasting() throws Exception {
        gameDAO.getGame(gameID).game().endGame();
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());

        white.send(new MakeMoveCommand("white-token", gameID, move(2, 5, 4, 5)));

        assertRejectedMove(white, before);
    }

    @Test
    public void successfulMovesAreNotBroadcastToOtherGames() throws Exception {
        int otherGameID = gameDAO.createGame(new GameData(
                0, "white", "black", "Other Game", new ChessGame()));
        SocketClient otherObserver = openSocket();
        connect(otherObserver, "observer-token", otherGameID);
        ChessMove move = move(2, 5, 4, 5);
        ChessGame expected = new ChessGame(gameDAO.getGame(gameID).game());
        expected.makeMove(move);

        white.send(new MakeMoveCommand("white-token", gameID, move));

        assertMoveDelivered(expected, white, "white", move);
        otherObserver.assertNoMessage();
        Assertions.assertEquals(new ChessGame(), gameDAO.getGame(otherGameID).game());
    }

    @ParameterizedTest
    @EnumSource(value = ChessPiece.PieceType.class, names = {"QUEEN", "ROOK", "BISHOP", "KNIGHT"})
    public void promotionUpdatesAllBoardsAndIncludesTheChosenPieceInTheNotice(
            ChessPiece.PieceType promotion) throws Exception {
        ChessBoard board = new ChessBoard();
        board.addPiece(new ChessPosition(1, 5),
                new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.KING));
        board.addPiece(new ChessPosition(8, 8),
                new ChessPiece(ChessGame.TeamColor.BLACK, ChessPiece.PieceType.KING));
        board.addPiece(new ChessPosition(7, 3),
                new ChessPiece(ChessGame.TeamColor.WHITE, ChessPiece.PieceType.PAWN));
        gameDAO.getGame(gameID).game().setBoard(board);
        ChessMove move = new ChessMove(new ChessPosition(7, 3), new ChessPosition(8, 3), promotion);
        ChessGame expected = new ChessGame(gameDAO.getGame(gameID).game());
        expected.makeMove(move);

        white.send(new MakeMoveCommand("white-token", gameID, move));

        String status = promotion == ChessPiece.PieceType.QUEEN || promotion == ChessPiece.PieceType.ROOK
                ? "black is in check." : null;
        assertMoveDelivered(expected, white, "white", move, status);
        Assertions.assertEquals(new ChessPiece(ChessGame.TeamColor.WHITE, promotion),
                gameDAO.getGame(gameID).game().getBoard().getPiece(new ChessPosition(8, 3)));
    }

    @Test
    public void failedSaveDoesNotAdvanceTheStoredBoardOrNotifyOtherClients() throws Exception {
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());
        controlledGames.failUpdates = true;
        ChessMove move = move(2, 5, 4, 5);

        white.send(new MakeMoveCommand("white-token", gameID, move));

        assertRejectedMove(white, before);
        controlledGames.failUpdates = false;
        ChessGame expected = new ChessGame(before);
        expected.makeMove(move);
        white.send(new MakeMoveCommand("white-token", gameID, move));
        assertMoveDelivered(expected, white, "white", move);
    }

    @Test
    public void pendingSavePreventsEarlyBroadcastsAndSerializesCompetingMoves() throws Exception {
        SocketClient competingPlayer = openSocket();
        connect(competingPlayer, "white-token", gameID);
        assertNotice(white, "white", "WHITE");
        assertNotice(black, "white", "WHITE");
        assertNotice(observer, "white", "WHITE");
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());
        ChessMove firstMove = move(2, 5, 4, 5);
        controlledGames.blockNextUpdate.set(true);

        try {
            white.send(new MakeMoveCommand("white-token", gameID, firstMove));
            Assertions.assertTrue(controlledGames.saveStarted.await(3, TimeUnit.SECONDS));
            competingPlayer.send(new MakeMoveCommand("white-token", gameID, move(2, 4, 4, 4)));
            Assertions.assertEquals(before, gameDAO.getGame(gameID).game());
            assertAllQuiet();
            competingPlayer.assertNoMessage();
        } finally {
            controlledGames.allowSave.countDown();
        }

        ChessGame expected = new ChessGame(before);
        expected.makeMove(firstMove);
        assertMoveDelivered(expected, white, "white", firstMove);
        Assertions.assertEquals(expected,
                Assertions.assertInstanceOf(LoadGameMessage.class, competingPlayer.receive()).getGame());
        assertNotice(competingPlayer, "white", "e2 to e4");
        assertError(competingPlayer);
        competingPlayer.assertNoMessage();
        Assertions.assertEquals(expected, gameDAO.getGame(gameID).game());
    }

    private static Stream<Arguments> invalidMoves() {
        return Stream.of(
                Arguments.of("black-token", move(7, 5, 5, 5)),
                Arguments.of("white-token", move(7, 5, 5, 5)),
                Arguments.of("observer-token", move(2, 5, 4, 5)),
                Arguments.of("white-token", move(1, 1, 1, 5)),
                Arguments.of("white-token", move(3, 5, 4, 5)),
                Arguments.of("white-token", move(0, 5, 4, 5)),
                Arguments.of("white-token", move(2, 5, 9, 5)),
                Arguments.of("white-token", move(2, 0, 4, 5)),
                Arguments.of("white-token", move(2, 5, 4, 9)),
                Arguments.of("white-token", new ChessMove(
                        new ChessPosition(2, 5), new ChessPosition(4, 5), ChessPiece.PieceType.KING)),
                Arguments.of("white-token", new ChessMove(
                        new ChessPosition(2, 5), new ChessPosition(4, 5), ChessPiece.PieceType.QUEEN)));
    }

    private static ChessMove move(int startRow, int startColumn, int endRow, int endColumn) {
        return new ChessMove(new ChessPosition(startRow, startColumn),
                new ChessPosition(endRow, endColumn), null);
    }

    private void assertRejectedMove(SocketClient sender, ChessGame before) throws Exception {
        assertError(sender);
        Assertions.assertEquals(before, gameDAO.getGame(gameID).game());
        assertAllQuiet();
    }

    private void assertAllQuiet() throws Exception {
        white.assertNoMessage();
        black.assertNoMessage();
        observer.assertNoMessage();
    }

    private void assertMoveDelivered(ChessGame expected, SocketClient mover, String username, ChessMove move)
            throws Exception {
        assertMoveDelivered(expected, mover, username, move, null);
    }

    private void assertMoveDelivered(
            ChessGame expected, SocketClient mover, String username, ChessMove move, String status)
            throws Exception {
        for (SocketClient client : List.of(white, black, observer)) {
            LoadGameMessage update = Assertions.assertInstanceOf(LoadGameMessage.class, client.receive());
            Assertions.assertEquals(expected, update.getGame());
            if (client != mover) {
                NotificationMessage notice = Assertions.assertInstanceOf(NotificationMessage.class, client.receive());
                Assertions.assertTrue(notice.getMessage().contains(username));
                Assertions.assertTrue(notice.getMessage().contains(square(move.getStartPosition())));
                Assertions.assertTrue(notice.getMessage().contains(square(move.getEndPosition())));
                if (move.getPromotionPiece() != null) {
                    Assertions.assertTrue(notice.getMessage().contains(move.getPromotionPiece().name()));
                }
            }
            if (status != null) {
                NotificationMessage notice = Assertions.assertInstanceOf(NotificationMessage.class, client.receive());
                Assertions.assertEquals(status, notice.getMessage());
            }
        }
        Assertions.assertEquals(expected, gameDAO.getGame(gameID).game());
        assertAllQuiet();
    }

    private String square(ChessPosition position) {
        return "" + (char) ('a' + position.getColumn() - 1) + position.getRow();
    }

}
