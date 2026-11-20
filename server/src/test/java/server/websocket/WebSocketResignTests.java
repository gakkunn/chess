package server.websocket;

import chess.ChessGame;
import chess.ChessMove;
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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class WebSocketResignTests extends WebSocketEndpointTestSupport {

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

    @ParameterizedTest
    @EnumSource(ChessGame.TeamColor.class)
    public void eitherPlayerCanResignWithoutLosingTheirSeatOrConnection(ChessGame.TeamColor color)
            throws Exception {
        ChessGame position = new ChessGame();
        position.makeMove(pawnMove());
        gameDAO.updateGame(new GameData(gameID, "white", "black", "Test Game", position));
        GameData before = snapshot();

        resign(player(color), username(color) + "-token", gameID);

        GameData completed = completedSnapshot(before);
        assertResigned(completed, username(color));
        for (ChessGame.TeamColor team : ChessGame.TeamColor.values()) {
            player(team).send(new MakeMoveCommand(username(team) + "-token", gameID, pawnMove()));
            Assertions.assertEquals("Error: The game is over", assertError(player(team)).getErrorMessage());
            resign(player(team), username(team) + "-token", gameID);
            Assertions.assertEquals("Error: The game is over", assertError(player(team)).getErrorMessage());
            assertAllQuiet();
        }

        SocketClient newObserver = openSocket();
        Assertions.assertEquals(completed.game(), connect(newObserver, "observer-token", gameID).getGame());
        for (SocketClient client : List.of(white, black, observer)) {
            assertNotice(client, "observer", "observer");
        }
        Assertions.assertEquals(completed, gameDAO.getGame(gameID));
    }

    @Test
    public void observersCannotResignAndRemainSubscribedAfterRejection() throws Exception {
        GameData before = snapshot();

        resign(observer, "observer-token", gameID);

        Assertions.assertEquals("Error: Observers cannot resign", assertError(observer).getErrorMessage());
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
        resign(white, "white-token", gameID);
        assertResigned(completedSnapshot(before), "white");
    }

    @Test
    public void completedGamesRejectResignationWithoutAnotherNotification() throws Exception {
        gameDAO.getGame(gameID).game().endGame();
        GameData before = snapshot();

        resign(black, "black-token", gameID);

        Assertions.assertEquals("Error: The game is over", assertError(black).getErrorMessage());
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    @ParameterizedTest
    @MethodSource("invalidResignRequests")
    public void invalidRequestsDoNotEndTheGameOrRemoveTheConnection(String token, Integer requestedGameID)
            throws Exception {
        GameData before = snapshot();

        resign(white, token, requestedGameID);

        assertError(white);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
        resign(white, "white-token", gameID);
        assertResigned(completedSnapshot(before), "white");
    }

    @Test
    public void anUnconnectedSocketCannotResignWithAValidToken() throws Exception {
        SocketClient unconnected = openSocket();
        GameData before = snapshot();

        resign(unconnected, "white-token", gameID);

        assertError(unconnected);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    @Test
    public void revokedAuthenticationCannotEndTheGame() throws Exception {
        GameData before = snapshot();
        authDAO.deleteAuth("white-token");

        resign(white, "white-token", gameID);

        Assertions.assertEquals("Error: unauthorized", assertError(white).getErrorMessage());
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    @Test
    public void formerPlayersCannotResignAfterTheirSlotHasBeenReassigned() throws Exception {
        GameData stored = gameDAO.getGame(gameID);
        gameDAO.updateGame(new GameData(gameID, "replacement", stored.blackUsername(),
                stored.gameName(), stored.game()));
        GameData before = snapshot();

        resign(white, "white-token", gameID);

        Assertions.assertEquals("Error: Observers cannot resign", assertError(white).getErrorMessage());
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    @Test
    public void resignationDoesNotAffectOtherGamesOrTheirObservers() throws Exception {
        int otherGameID = gameDAO.createGame(new GameData(
                0, "white", "black", "Other Game", new ChessGame()));
        SocketClient otherObserver = openSocket();
        connect(otherObserver, "observer-token", otherGameID);
        GameData before = snapshot();

        resign(white, "white-token", otherGameID);

        assertError(white);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        Assertions.assertFalse(gameDAO.getGame(otherGameID).game().isGameOver());
        assertAllQuiet();
        otherObserver.assertNoMessage();
        resign(white, "white-token", gameID);
        assertResigned(completedSnapshot(before), "white");
        Assertions.assertFalse(gameDAO.getGame(otherGameID).game().isGameOver());
        otherObserver.assertNoMessage();
    }

    @Test
    public void failedSavesKeepTheGamePlayableAndAllowRetryingResignation() throws Exception {
        GameData before = snapshot();
        controlledGames.failUpdates = true;

        resign(white, "white-token", gameID);

        assertError(white);
        Assertions.assertEquals(before, gameDAO.getGame(gameID));
        assertAllQuiet();
        controlledGames.failUpdates = false;
        ChessGame afterMove = new ChessGame(before.game());
        afterMove.makeMove(pawnMove());
        white.send(new MakeMoveCommand("white-token", gameID, pawnMove()));
        for (SocketClient client : List.of(white, black, observer)) {
            Assertions.assertEquals(afterMove,
                    Assertions.assertInstanceOf(LoadGameMessage.class, client.receive()).getGame());
            if (client != white) {
                assertNotice(client, "white", "e2 to e4");
            }
        }
        assertAllQuiet();
        GameData playable = snapshot();
        resign(white, "white-token", gameID);
        assertResigned(completedSnapshot(playable), "white");
    }

    @Test
    public void pendingResignationBlocksCompetingResignationsAndMovesUntilTheSaveFinishes() throws Exception {
        GameData before = snapshot();
        controlledGames.blockNextUpdate.set(true);

        try {
            resign(white, "white-token", gameID);
            Assertions.assertTrue(controlledGames.saveStarted.await(3, TimeUnit.SECONDS));
            resign(black, "black-token", gameID);
            white.send(new MakeMoveCommand("white-token", gameID, pawnMove()));
            Assertions.assertEquals(before, gameDAO.getGame(gameID));
            assertAllQuiet();
        } finally {
            controlledGames.allowSave.countDown();
        }

        for (SocketClient client : List.of(white, black, observer)) {
            assertNotice(client, "white", "resigned. The game is over.");
        }
        Assertions.assertEquals("Error: The game is over", assertError(white).getErrorMessage());
        Assertions.assertEquals("Error: The game is over", assertError(black).getErrorMessage());
        Assertions.assertEquals(completedSnapshot(before), gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    private static Stream<Arguments> invalidResignRequests() {
        return Stream.of(
                Arguments.of("bad-token", 1),
                Arguments.of(null, 1),
                Arguments.of("", 1),
                Arguments.of("black-token", 1),
                Arguments.of("white-token", null),
                Arguments.of("white-token", 0),
                Arguments.of("white-token", -1),
                Arguments.of("white-token", 999));
    }

    private ChessMove pawnMove() {
        return new ChessMove(new ChessPosition(2, 5), new ChessPosition(4, 5), null);
    }

    private GameData snapshot() throws Exception {
        GameData stored = gameDAO.getGame(gameID);
        return new GameData(stored.gameID(), stored.whiteUsername(), stored.blackUsername(),
                stored.gameName(), new ChessGame(stored.game()));
    }

    private GameData completedSnapshot(GameData before) {
        ChessGame completed = new ChessGame(before.game());
        completed.endGame();
        return new GameData(before.gameID(), before.whiteUsername(), before.blackUsername(),
                before.gameName(), completed);
    }

    private SocketClient player(ChessGame.TeamColor color) {
        return color == ChessGame.TeamColor.WHITE ? white : black;
    }

    private String username(ChessGame.TeamColor color) {
        return color == ChessGame.TeamColor.WHITE ? "white" : "black";
    }

    private void assertResigned(GameData expected, String username) throws Exception {
        for (SocketClient client : List.of(white, black, observer)) {
            assertNotice(client, username, "resigned. The game is over.");
        }
        Assertions.assertEquals(expected, gameDAO.getGame(gameID));
        assertAllQuiet();
    }

    private void assertAllQuiet() throws Exception {
        white.assertNoMessage();
        black.assertNoMessage();
        observer.assertNoMessage();
    }
}
