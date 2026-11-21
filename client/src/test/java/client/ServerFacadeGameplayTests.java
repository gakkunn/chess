package client;

import chess.ChessGame;
import chess.ChessMove;
import chess.ChessPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import websocket.messages.ErrorMessage;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ServerFacadeGameplayTests extends GameplayFacadeTestSupport {

    @Test
    public void playersAndObserversReceiveTypedMoveUpdatesAndNotifications() throws Exception {
        connectParticipants();
        ChessMove whiteMove = move(2, 5, 4, 5);
        ChessGame expected = new ChessGame();
        expected.makeMove(whiteMove);

        white.makeMove(whiteAuth.authToken(), gameID, whiteMove);

        assertBoards(expected);
        Assertions.assertTrue(blackMessages.take(NotificationMessage.class).getMessage().contains("white moved e2 to e4"));
        observerMessages.take(NotificationMessage.class);
        whiteMessages.assertQuiet();
        ChessMove blackMove = move(7, 5, 5, 5);
        expected.makeMove(blackMove);
        black.makeMove(blackAuth.authToken(), gameID, blackMove);
        assertBoards(expected);
        whiteMessages.take(NotificationMessage.class);
        observerMessages.take(NotificationMessage.class);
        blackMessages.assertQuiet();
        Assertions.assertEquals(expected, games.getGame(gameID).game());
        Assertions.assertEquals(1, white.listGames(whiteAuth.authToken()).size());
    }

    @Test
    public void observersCanConnectWithoutClaimingAPlayerSlot() throws Exception {
        int openGame = white.createGame(whiteAuth.authToken(), "Open Game");

        observer.connectGame(observerAuth.authToken(), openGame, observerMessages);

        observerMessages.take(LoadGameMessage.class);
        Assertions.assertNull(games.getGame(openGame).whiteUsername());
        Assertions.assertNull(games.getGame(openGame).blackUsername());
    }

    @Test
    public void serverErrorsArriveAsynchronouslyAndDoNotCloseTheConnection() throws Exception {
        connectParticipants();

        white.makeMove(whiteAuth.authToken(), gameID, move(1, 1, 1, 5));

        Assertions.assertTrue(whiteMessages.take(ErrorMessage.class).getErrorMessage().contains("not legal"));
        Assertions.assertTrue(white.isConnected());
        blackMessages.assertQuiet();
        observerMessages.assertQuiet();
        white.makeMove(whiteAuth.authToken(), gameID, move(2, 5, 4, 5));
        ChessGame expected = new ChessGame();
        expected.makeMove(move(2, 5, 4, 5));
        assertBoards(expected);
        blackMessages.take(NotificationMessage.class);
        observerMessages.take(NotificationMessage.class);
    }

    @Test
    public void malformedBoardMessagesDoNotStopLaterMessagesOnTheSameSocket() throws Exception {
        games.getGame(gameID).game().setBoard(null);

        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);

        Assertions.assertTrue(whiteMessages.take(ErrorMessage.class).getErrorMessage().contains("unreadable"));
        Assertions.assertTrue(white.isConnected());
        games.getGame(gameID).game().setBoard(new ChessGame().getBoard());
        observer.connectGame(observerAuth.authToken(), gameID, observerMessages);
        observerMessages.take(LoadGameMessage.class);
        whiteMessages.take(NotificationMessage.class);
        Assertions.assertTrue(white.isConnected());
    }

    @Test
    public void aRejectedConnectCanBeDisconnectedAndRetried() throws Exception {
        white.connectGame("bad-token", gameID, whiteMessages);

        Assertions.assertEquals("Error: unauthorized", whiteMessages.take(ErrorMessage.class).getErrorMessage());
        white.disconnect();
        Assertions.assertFalse(white.isConnected());
        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);
        whiteMessages.take(LoadGameMessage.class);
        Assertions.assertTrue(white.isConnected());
        Assertions.assertEquals(0, whiteMessages.disconnectCount.get());
    }

    @Test
    public void leaveStopsGameUpdatesAndResignNotifiesAllRemainingParticipants() throws Exception {
        connectParticipants();

        white.leaveGame(whiteAuth.authToken(), gameID);

        Assertions.assertTrue(blackMessages.take(NotificationMessage.class).getMessage().contains("white left"));
        observerMessages.take(NotificationMessage.class);
        whiteMessages.assertQuiet();
        Assertions.assertNull(games.getGame(gameID).whiteUsername());
        Assertions.assertTrue(white.isConnected());
        white.disconnect();
        black.resignGame(blackAuth.authToken(), gameID);
        Assertions.assertTrue(blackMessages.take(NotificationMessage.class).getMessage().contains("black resigned"));
        observerMessages.take(NotificationMessage.class);
        whiteMessages.assertQuiet();
        Assertions.assertTrue(black.isConnected());
        Assertions.assertEquals("black", games.getGame(gameID).blackUsername());
        Assertions.assertTrue(games.getGame(gameID).game().isGameOver());
    }

    @Test
    public void disconnectKeepsTheSeatAndReconnectLoadsTheCurrentBoard() throws Exception {
        connectParticipants();
        white.disconnect();
        ServerFacade replacementConnection = facade(port);
        RecordingObserver replacementMessages = new RecordingObserver();
        replacementConnection.connectGame(whiteAuth.authToken(), gameID, replacementMessages);
        replacementMessages.take(LoadGameMessage.class);
        blackMessages.take(NotificationMessage.class);
        observerMessages.take(NotificationMessage.class);
        replacementConnection.makeMove(whiteAuth.authToken(), gameID, move(2, 5, 4, 5));
        ChessGame expected = new ChessGame();
        expected.makeMove(move(2, 5, 4, 5));
        replacementMessages.take(LoadGameMessage.class);
        Assertions.assertEquals(expected, blackMessages.take(LoadGameMessage.class).getGame());
        blackMessages.take(NotificationMessage.class);
        observerMessages.take(LoadGameMessage.class);
        observerMessages.take(NotificationMessage.class);
        whiteMessages.assertQuiet();
        replacementConnection.disconnect();

        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);

        Assertions.assertEquals(expected, whiteMessages.take(LoadGameMessage.class).getGame());
        blackMessages.take(NotificationMessage.class);
        observerMessages.take(NotificationMessage.class);
        Assertions.assertEquals("white", games.getGame(gameID).whiteUsername());
        Assertions.assertTrue(white.isConnected());
        Assertions.assertEquals(0, whiteMessages.disconnectCount.get());
    }

    @Test
    public void unexpectedDisconnectIsReportedOnceAndCanBeReconnected() throws Exception {
        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);
        whiteMessages.take(LoadGameMessage.class);
        int originalPort = port;

        stopServer();

        Assertions.assertTrue(whiteMessages.take(ErrorMessage.class).getErrorMessage().contains("connection"));
        Assertions.assertTrue(whiteMessages.disconnected.await(3, TimeUnit.SECONDS));
        Assertions.assertEquals(1, whiteMessages.disconnectCount.get());
        Assertions.assertFalse(white.isConnected());
        Assertions.assertThrows(ServerFacadeException.class,
                () -> white.makeMove(whiteAuth.authToken(), gameID, move(2, 5, 4, 5)));
        startServer(originalPort);
        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);
        whiteMessages.take(LoadGameMessage.class);
        Assertions.assertTrue(white.isConnected());
        white.makeMove(whiteAuth.authToken(), gameID, move(2, 5, 4, 5));
        whiteMessages.take(LoadGameMessage.class);
        Assertions.assertEquals(1, whiteMessages.disconnectCount.get());
    }

    @Test
    public void handshakeFailuresReleaseResourcesAndCanBeRetried() throws Exception {
        int unusedPort;
        try (ServerSocket reservation = new ServerSocket(0)) {
            unusedPort = reservation.getLocalPort();
        }
        ServerFacade retrying = facade(unusedPort);
        RecordingObserver messages = new RecordingObserver();

        Assertions.assertThrows(ServerFacadeException.class,
                () -> retrying.connectGame(whiteAuth.authToken(), gameID, messages));

        Assertions.assertFalse(retrying.isConnected());
        messages.assertQuiet();
        startServer(unusedPort);
        retrying.connectGame(whiteAuth.authToken(), gameID, messages);
        messages.take(LoadGameMessage.class);
        Assertions.assertTrue(retrying.isConnected());
    }

    @Test
    public void duplicateConnectDoesNotReplaceTheCurrentObserver() throws Exception {
        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);
        whiteMessages.take(LoadGameMessage.class);
        RecordingObserver secondObserver = new RecordingObserver();

        Assertions.assertThrows(ServerFacadeException.class,
                () -> white.connectGame(whiteAuth.authToken(), gameID, secondObserver));

        observer.connectGame(observerAuth.authToken(), gameID, observerMessages);
        observerMessages.take(LoadGameMessage.class);
        whiteMessages.take(NotificationMessage.class);
        secondObserver.assertQuiet();
        Assertions.assertTrue(white.isConnected());
    }

    @Test
    public void observersCanDisconnectFromInsideTheMessageCallback() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        ServerMessageObserver callback = message -> {
            whiteMessages.notify(message);
            if (message instanceof NotificationMessage) {
                white.disconnect();
                disconnected.countDown();
            }
        };
        white.connectGame(whiteAuth.authToken(), gameID, callback);
        whiteMessages.take(LoadGameMessage.class);

        observer.connectGame(observerAuth.authToken(), gameID, observerMessages);

        observerMessages.take(LoadGameMessage.class);
        Assertions.assertTrue(disconnected.await(3, TimeUnit.SECONDS));
        Assertions.assertFalse(white.isConnected());
        whiteMessages.take(NotificationMessage.class);
        whiteMessages.assertQuiet();
    }

    @Test
    public void disconnectedAndClosedFacadesRejectSendsAndCloseIsIdempotent() throws Exception {
        Assertions.assertThrows(ServerFacadeException.class,
                () -> white.makeMove(whiteAuth.authToken(), gameID, move(2, 5, 4, 5)));
        Assertions.assertThrows(ServerFacadeException.class, () -> white.leaveGame(whiteAuth.authToken(), gameID));
        Assertions.assertThrows(ServerFacadeException.class, () -> white.resignGame(whiteAuth.authToken(), gameID));
        Assertions.assertThrows(NullPointerException.class, () -> white.connectGame(whiteAuth.authToken(), gameID, null));
        white.connectGame(whiteAuth.authToken(), gameID, whiteMessages);
        whiteMessages.take(LoadGameMessage.class);

        white.close();
        white.close();
        white.disconnect();

        Assertions.assertFalse(white.isConnected());
        Assertions.assertThrows(ServerFacadeException.class,
                () -> white.connectGame(whiteAuth.authToken(), gameID, whiteMessages));
        Assertions.assertThrows(ServerFacadeException.class, () -> white.listGames(whiteAuth.authToken()));
        Assertions.assertEquals(0, whiteMessages.disconnectCount.get());
    }

    private ChessMove move(int fromRow, int fromColumn, int toRow, int toColumn) {
        return new ChessMove(new ChessPosition(fromRow, fromColumn), new ChessPosition(toRow, toColumn), null);
    }

    private void assertBoards(ChessGame expected) throws Exception {
        for (RecordingObserver messages : List.of(whiteMessages, blackMessages, observerMessages)) {
            Assertions.assertEquals(expected, messages.take(LoadGameMessage.class).getGame());
        }
    }
}
