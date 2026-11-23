package client;

import chess.ChessGame;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

public class ChessClientExitTests extends GameplayFacadeTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"white", "black"})
    void leavingReleasesOnlyThePlayersSeatAndReturnsToPregame(String username) throws Exception {
        TestGameplayClient player = enter(username);
        RecordingObserver audience = watchFromAnotherConnection();
        ChessGame.TeamColor color = username.equals("white") ? ChessGame.TeamColor.WHITE : ChessGame.TeamColor.BLACK;

        Assertions.assertEquals("Left 'Live Game'.", player.evaluate("leave"));
        Assertions.assertEquals(username + " left the game.", audience.take(NotificationMessage.class).getMessage());
        GameData game = games.getGame(gameID);
        Assertions.assertEquals(username.equals("white") ? null : "white", game.whiteUsername());
        Assertions.assertEquals(username.equals("black") ? null : "black", game.blackUsername());
        Assertions.assertEquals(new ChessGame(), game.game());
        Assertions.assertEquals(ClientState.SIGNED_IN, player.client.getState());
        Assertions.assertFalse(player.facade.isConnected());
        Assertions.assertTrue(player.evaluate("reconnect").contains("no game"));
        Assertions.assertTrue(player.evaluate("list").contains("Live Game"));

        observer.joinGame(observerAuth.authToken(), color, gameID);
        Assertions.assertEquals("observer", color == ChessGame.TeamColor.WHITE
                ? games.getGame(gameID).whiteUsername() : games.getGame(gameID).blackUsername());
    }

    @Test
    void observerCanLeaveWithoutChangingThePlayersOrBoard() throws Exception {
        TestGameplayClient watcher = enter("observer");
        RecordingObserver audience = watchFromAnotherConnection();
        GameData before = games.getGame(gameID);

        Assertions.assertEquals("Left 'Live Game'.", watcher.evaluate("leave"));
        Assertions.assertEquals("observer left the game.", audience.take(NotificationMessage.class).getMessage());
        Assertions.assertEquals(before, games.getGame(gameID));
        Assertions.assertEquals(ClientState.SIGNED_IN, watcher.client.getState());
        Assertions.assertFalse(watcher.facade.isConnected());
    }

    @ParameterizedTest
    @ValueSource(strings = {"white", "black"})
    void confirmedResignationEndsTheGameForEveryoneWithoutLeaving(String username) throws Exception {
        TestGameplayClient whitePlayer = enter("white");
        TestGameplayClient blackPlayer = enter("black");
        TestGameplayClient watcher = enter("observer");
        TestGameplayClient resigning = username.equals("white") ? whitePlayer : blackPlayer;
        TestGameplayClient remaining = username.equals("white") ? blackPlayer : whitePlayer;
        String whiteBoard = whitePlayer.evaluate("redraw");
        String blackBoard = blackPlayer.evaluate("redraw");
        whitePlayer.evaluate("resign");
        blackPlayer.evaluate("resign");
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());

        Assertions.assertEquals("Resignation submitted.", resigning.evaluate("yes"));
        String notice = username + " resigned. The game is over.";
        whitePlayer.await(notice::equals);
        blackPlayer.await(notice::equals);
        watcher.await(notice::equals);

        GameData game = games.getGame(gameID);
        Assertions.assertTrue(game.game().isGameOver());
        Assertions.assertEquals("white", game.whiteUsername());
        Assertions.assertEquals("black", game.blackUsername());
        Assertions.assertEquals(new ChessGame().getBoard(), game.game().getBoard());
        for (TestGameplayClient participant : new TestGameplayClient[]{whitePlayer, blackPlayer, watcher}) {
            Assertions.assertEquals(ClientState.IN_GAME, participant.client.getState());
            Assertions.assertTrue(participant.facade.isConnected());
        }
        Assertions.assertEquals(whiteBoard, whitePlayer.evaluate("redraw"));
        Assertions.assertEquals(blackBoard, blackPlayer.evaluate("redraw"));
        Assertions.assertTrue(whitePlayer.evaluate("move e2 e4").contains("game is over"));
        Assertions.assertTrue(blackPlayer.evaluate("move e7 e5").contains("game is over"));
        Assertions.assertTrue(remaining.evaluate("yes").contains("no resignation"));
        Assertions.assertTrue(resigning.evaluate("resign").contains("already over"));

        resigning.evaluate("leave");
        remaining.await(text -> text.equals(username + " left the game."));
        Assertions.assertEquals(ClientState.SIGNED_IN, resigning.client.getState());
        Assertions.assertTrue(games.getGame(gameID).game().isGameOver());
        Assertions.assertNull(username.equals("white")
                ? games.getGame(gameID).whiteUsername() : games.getGame(gameID).blackUsername());
    }

    @Test
    void cancelledResignationAllowsPlayToContinue() throws Exception {
        TestGameplayClient player = enter("white");
        RecordingObserver audience = watchFromAnotherConnection();
        player.evaluate("resign");
        Assertions.assertEquals("Resignation cancelled.", player.evaluate("no"));
        Assertions.assertTrue(player.evaluate("yes").contains("no resignation"));
        player.evaluate("move e2 e4");

        ChessGame updated = audience.take(LoadGameMessage.class).getGame();
        Assertions.assertFalse(updated.isGameOver());
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, updated.getTeamTurn());
        Assertions.assertTrue(audience.take(NotificationMessage.class).getMessage().contains("e2 to e4"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"quit", "close"})
    void closingDuringConfirmationNotifiesOthersAndReleasesTheSeatOnce(String command) throws Exception {
        TestGameplayClient player = enter("white");
        RecordingObserver audience = watchFromAnotherConnection();
        player.evaluate("resign");
        if (command.equals("quit")) {
            Assertions.assertEquals("Goodbye.", player.evaluate("quit"));
        } else {
            player.client.close();
        }
        player.client.close();

        Assertions.assertEquals("white left the game.", audience.take(NotificationMessage.class).getMessage());
        audience.assertQuiet();
        Assertions.assertNull(games.getGame(gameID).whiteUsername());
        Assertions.assertEquals("black", games.getGame(gameID).blackUsername());
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
        Assertions.assertEquals(ClientState.QUIT, player.client.getState());
        Assertions.assertFalse(player.facade.isConnected());
    }

    @Test
    void expiredSessionOnResignationDoesNotEndTheGame() throws Exception {
        TestGameplayClient player = enter("white");
        player.evaluate("resign");
        auth.clear();
        player.evaluate("yes");

        player.await(text -> text.contains("sign in again"));
        Assertions.assertEquals(ClientState.SIGNED_OUT, player.client.getState());
        Assertions.assertFalse(player.facade.isConnected());
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
    }

    private RecordingObserver watchFromAnotherConnection() throws Exception {
        RecordingObserver audience = new RecordingObserver();
        facade(port).connectGame(blackAuth.authToken(), gameID, audience);
        audience.take(LoadGameMessage.class);
        return audience;
    }
}
