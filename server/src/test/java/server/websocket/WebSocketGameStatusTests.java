package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import testutil.GameplayPositions;
import websocket.commands.MakeMoveCommand;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class WebSocketGameStatusTests extends ConnectedGameTestSupport {

    @ParameterizedTest
    @EnumSource(ChessGame.TeamColor.class)
    public void checkNotifiesEveryoneAndTheGameContinuesAfterEscape(ChessGame.TeamColor movingTeam)
            throws Exception {
        installPosition(GameplayPositions.beforeCheck(movingTeam));
        ChessMove move = GameplayPositions.checkingMove(movingTeam);
        ChessGame expected = positionAfter(move);
        ChessGame.TeamColor checkedTeam = expected.getTeamTurn();
        Assertions.assertTrue(expected.isInCheck(checkedTeam));
        Assertions.assertFalse(expected.isInCheckmate(checkedTeam));

        sendMove(movingTeam, move);

        assertMoveAndStatus(expected, movingTeam, username(checkedTeam) + " is in check.");
        Assertions.assertFalse(gameDAO.getGame(gameID).game().isGameOver());
        ChessMove escape = GameplayPositions.escapeCheckMove(movingTeam);
        expected.makeMove(escape);
        Assertions.assertFalse(expected.isInCheck(checkedTeam));
        sendMove(checkedTeam, escape);
        assertMoveAndStatus(expected, checkedTeam, null);
        Assertions.assertFalse(gameDAO.getGame(gameID).game().isGameOver());
    }

    @ParameterizedTest
    @EnumSource(ChessGame.TeamColor.class)
    public void checkmateNotifiesEveryoneOnceAndPreventsFurtherMoves(ChessGame.TeamColor movingTeam)
            throws Exception {
        installPosition(GameplayPositions.beforeCheckmate(movingTeam));
        ChessMove move = GameplayPositions.matingMove(movingTeam);
        ChessGame expected = positionAfter(move);
        ChessGame.TeamColor losingTeam = expected.getTeamTurn();
        Assertions.assertTrue(expected.isInCheckmate(losingTeam));
        expected.endGame();

        sendMove(movingTeam, move);

        assertMoveAndStatus(expected, movingTeam,
                username(losingTeam) + " is in checkmate. The game is over.");
        assertEndedGameRejectsBothPlayers(expected);
        Assertions.assertTrue(connect(player(movingTeam), username(movingTeam) + "-token", gameID)
                .getGame().isGameOver());
        assertAllQuiet();
    }

    @ParameterizedTest
    @EnumSource(ChessGame.TeamColor.class)
    public void stalemateNotifiesEveryoneOfTheDrawAndPreventsFurtherMoves(ChessGame.TeamColor movingTeam)
            throws Exception {
        installPosition(GameplayPositions.beforeStalemate(movingTeam));
        ChessMove move = GameplayPositions.stalematingMove(movingTeam);
        ChessGame expected = positionAfter(move);
        ChessGame.TeamColor stuckTeam = expected.getTeamTurn();
        Assertions.assertTrue(expected.isInStalemate(stuckTeam));
        Assertions.assertFalse(expected.isInCheck(stuckTeam));
        expected.endGame();

        sendMove(movingTeam, move);

        assertMoveAndStatus(expected, movingTeam,
                "Stalemate: " + username(stuckTeam) + " has no legal moves. The game is a draw.");
        assertEndedGameRejectsBothPlayers(expected);
    }

    @Test
    public void statusMessagesDoNotReachAnotherGame() throws Exception {
        int otherGameID = gameDAO.createGame(new GameData(
                0, null, null, "Other Game", new ChessGame()));
        SocketClient otherObserver = openSocket();
        connect(otherObserver, "observer-token", otherGameID);
        installPosition(GameplayPositions.beforeCheckmate(ChessGame.TeamColor.WHITE));
        ChessMove move = GameplayPositions.matingMove(ChessGame.TeamColor.WHITE);
        ChessGame expected = positionAfter(move);
        expected.endGame();

        sendMove(ChessGame.TeamColor.WHITE, move);

        assertMoveAndStatus(expected, ChessGame.TeamColor.WHITE,
                "black is in checkmate. The game is over.");
        otherObserver.assertNoMessage();
    }

    @Test
    public void aFailedCompletionSaveDoesNotEndTheStoredGameOrBroadcastStatus() throws Exception {
        installPosition(GameplayPositions.beforeCheckmate(ChessGame.TeamColor.WHITE));
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());
        ChessMove move = GameplayPositions.matingMove(ChessGame.TeamColor.WHITE);
        controlledGames.failUpdates = true;

        sendMove(ChessGame.TeamColor.WHITE, move);

        assertError(white);
        Assertions.assertEquals(before, gameDAO.getGame(gameID).game());
        Assertions.assertFalse(gameDAO.getGame(gameID).game().isGameOver());
        assertAllQuiet();
        controlledGames.failUpdates = false;
        ChessGame expected = positionAfter(move);
        expected.endGame();
        sendMove(ChessGame.TeamColor.WHITE, move);
        assertMoveAndStatus(expected, ChessGame.TeamColor.WHITE,
                "black is in checkmate. The game is over.");
    }

    @Test
    public void completionMessagesWaitUntilTheDatabaseSaveHasFinished() throws Exception {
        installPosition(GameplayPositions.beforeStalemate(ChessGame.TeamColor.WHITE));
        ChessGame before = new ChessGame(gameDAO.getGame(gameID).game());
        ChessMove move = GameplayPositions.stalematingMove(ChessGame.TeamColor.WHITE);
        controlledGames.blockNextUpdate.set(true);

        try {
            sendMove(ChessGame.TeamColor.WHITE, move);
            Assertions.assertTrue(controlledGames.saveStarted.await(3, TimeUnit.SECONDS));
            Assertions.assertEquals(before, gameDAO.getGame(gameID).game());
            assertAllQuiet();
        } finally {
            controlledGames.allowSave.countDown();
        }

        ChessGame expected = new ChessGame(before);
        expected.makeMove(move);
        expected.endGame();
        assertMoveAndStatus(expected, ChessGame.TeamColor.WHITE,
                "Stalemate: black has no legal moves. The game is a draw.");
    }

    @Test
    public void anUnassignedColorDoesNotProduceANullPlayerName() throws Exception {
        ChessGame position = GameplayPositions.beforeCheck(ChessGame.TeamColor.WHITE);
        gameDAO.updateGame(new GameData(gameID, "white", null, "Open Game", position));
        ChessMove move = GameplayPositions.checkingMove(ChessGame.TeamColor.WHITE);
        ChessGame expected = positionAfter(move);

        sendMove(ChessGame.TeamColor.WHITE, move);

        assertMoveAndStatus(expected, ChessGame.TeamColor.WHITE, "BLACK player is in check.");
    }

    private void installPosition(ChessGame position) throws Exception {
        gameDAO.updateGame(new GameData(gameID, "white", "black", "Test Game", position));
    }

    private ChessGame positionAfter(ChessMove move) throws Exception {
        ChessGame expected = new ChessGame(gameDAO.getGame(gameID).game());
        expected.makeMove(move);
        return expected;
    }

    private void sendMove(ChessGame.TeamColor team, ChessMove move) throws Exception {
        player(team).send(new MakeMoveCommand(username(team) + "-token", gameID, move));
    }

    private void assertMoveAndStatus(ChessGame expected, ChessGame.TeamColor movingTeam, String status)
            throws Exception {
        for (SocketClient client : List.of(white, black, observer)) {
            LoadGameMessage update = Assertions.assertInstanceOf(LoadGameMessage.class, client.receive());
            Assertions.assertEquals(expected, update.getGame());
            if (client != player(movingTeam)) {
                NotificationMessage moveNotice = Assertions.assertInstanceOf(
                        NotificationMessage.class, client.receive());
                Assertions.assertTrue(moveNotice.getMessage().startsWith(username(movingTeam) + " moved "));
            }
            if (status != null) {
                NotificationMessage statusNotice = Assertions.assertInstanceOf(
                        NotificationMessage.class, client.receive());
                Assertions.assertEquals(status, statusNotice.getMessage());
            }
        }
        Assertions.assertEquals(expected, gameDAO.getGame(gameID).game());
        assertAllQuiet();
    }

    private void assertEndedGameRejectsBothPlayers(ChessGame expected) throws Exception {
        ChessMove attempt = new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
        for (ChessGame.TeamColor team : ChessGame.TeamColor.values()) {
            sendMove(team, attempt);
            Assertions.assertTrue(assertError(player(team)).getErrorMessage().contains("The game is over"));
            Assertions.assertEquals(expected, gameDAO.getGame(gameID).game());
            assertAllQuiet();
        }
    }
}
