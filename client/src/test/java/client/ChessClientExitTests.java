package client;

import chess.ChessGame;
import model.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import websocket.messages.LoadGameMessage;
import websocket.messages.NotificationMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class ChessClientExitTests extends GameplayFacadeTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"white", "black"})
    void leavingReleasesOnlyThePlayersSeatAndReturnsToPregame(String username) throws Exception {
        LiveClient player = enter(username);
        RecordingObserver audience = watchFromAnotherConnection();
        ChessGame.TeamColor color = username.equals("white") ? ChessGame.TeamColor.WHITE : ChessGame.TeamColor.BLACK;

        Assertions.assertEquals("Left 'Live Game'.", player.client.evaluate("leave"));
        Assertions.assertEquals(username + " left the game.", audience.take(NotificationMessage.class).getMessage());
        GameData game = games.getGame(gameID);
        Assertions.assertEquals(username.equals("white") ? null : "white", game.whiteUsername());
        Assertions.assertEquals(username.equals("black") ? null : "black", game.blackUsername());
        Assertions.assertEquals(new ChessGame(), game.game());
        Assertions.assertEquals(ClientState.SIGNED_IN, player.client.getState());
        Assertions.assertFalse(player.facade.isConnected());
        Assertions.assertTrue(player.client.evaluate("reconnect").contains("no game"));
        Assertions.assertTrue(player.client.evaluate("list").contains("Live Game"));

        observer.joinGame(observerAuth.authToken(), color, gameID);
        Assertions.assertEquals("observer", color == ChessGame.TeamColor.WHITE
                ? games.getGame(gameID).whiteUsername() : games.getGame(gameID).blackUsername());
    }

    @Test
    void observerCanLeaveWithoutChangingThePlayersOrBoard() throws Exception {
        LiveClient watcher = enter("observer");
        RecordingObserver audience = watchFromAnotherConnection();
        GameData before = games.getGame(gameID);

        Assertions.assertEquals("Left 'Live Game'.", watcher.client.evaluate("leave"));
        Assertions.assertEquals("observer left the game.", audience.take(NotificationMessage.class).getMessage());
        Assertions.assertEquals(before, games.getGame(gameID));
        Assertions.assertEquals(ClientState.SIGNED_IN, watcher.client.getState());
        Assertions.assertFalse(observer.isConnected());
    }

    @ParameterizedTest
    @ValueSource(strings = {"white", "black"})
    void confirmedResignationEndsTheGameForEveryoneWithoutLeaving(String username) throws Exception {
        LiveClient whitePlayer = enter("white");
        LiveClient blackPlayer = enter("black");
        LiveClient watcher = enter("observer");
        LiveClient resigning = username.equals("white") ? whitePlayer : blackPlayer;
        LiveClient remaining = username.equals("white") ? blackPlayer : whitePlayer;
        String whiteBoard = whitePlayer.client.evaluate("redraw");
        String blackBoard = blackPlayer.client.evaluate("redraw");
        whitePlayer.client.evaluate("resign");
        blackPlayer.client.evaluate("resign");
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());

        Assertions.assertEquals("Resignation submitted.", resigning.client.evaluate("yes"));
        String notice = username + " resigned. The game is over.";
        await(whitePlayer, notice::equals);
        await(blackPlayer, notice::equals);
        await(watcher, notice::equals);

        GameData game = games.getGame(gameID);
        Assertions.assertTrue(game.game().isGameOver());
        Assertions.assertEquals("white", game.whiteUsername());
        Assertions.assertEquals("black", game.blackUsername());
        Assertions.assertEquals(new ChessGame().getBoard(), game.game().getBoard());
        for (LiveClient participant : new LiveClient[]{whitePlayer, blackPlayer, watcher}) {
            Assertions.assertEquals(ClientState.IN_GAME, participant.client.getState());
            Assertions.assertTrue(participant.facade.isConnected());
        }
        Assertions.assertEquals(whiteBoard, whitePlayer.client.evaluate("redraw"));
        Assertions.assertEquals(blackBoard, blackPlayer.client.evaluate("redraw"));
        Assertions.assertTrue(whitePlayer.client.evaluate("move e2 e4").contains("game is over"));
        Assertions.assertTrue(blackPlayer.client.evaluate("move e7 e5").contains("game is over"));
        Assertions.assertTrue(remaining.client.evaluate("yes").contains("no resignation"));
        Assertions.assertTrue(resigning.client.evaluate("resign").contains("already over"));

        resigning.client.evaluate("leave");
        await(remaining, text -> text.equals(username + " left the game."));
        Assertions.assertEquals(ClientState.SIGNED_IN, resigning.client.getState());
        Assertions.assertTrue(games.getGame(gameID).game().isGameOver());
        Assertions.assertNull(username.equals("white")
                ? games.getGame(gameID).whiteUsername() : games.getGame(gameID).blackUsername());
    }

    @Test
    void cancelledResignationAllowsPlayToContinue() throws Exception {
        LiveClient player = enter("white");
        RecordingObserver audience = watchFromAnotherConnection();
        player.client.evaluate("resign");
        Assertions.assertEquals("Resignation cancelled.", player.client.evaluate("no"));
        Assertions.assertTrue(player.client.evaluate("yes").contains("no resignation"));
        player.client.evaluate("move e2 e4");

        ChessGame updated = audience.take(LoadGameMessage.class).getGame();
        Assertions.assertFalse(updated.isGameOver());
        Assertions.assertEquals(ChessGame.TeamColor.BLACK, updated.getTeamTurn());
        Assertions.assertTrue(audience.take(NotificationMessage.class).getMessage().contains("e2 to e4"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"quit", "close"})
    void closingDuringConfirmationNotifiesOthersAndReleasesTheSeatOnce(String command) throws Exception {
        LiveClient player = enter("white");
        RecordingObserver audience = watchFromAnotherConnection();
        player.client.evaluate("resign");
        if (command.equals("quit")) {
            Assertions.assertEquals("Goodbye.", player.client.evaluate("quit"));
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
        Assertions.assertFalse(white.isConnected());
    }

    @Test
    void expiredSessionOnResignationDoesNotEndTheGame() throws Exception {
        LiveClient player = enter("white");
        player.client.evaluate("resign");
        auth.clear();
        player.client.evaluate("yes");

        await(player, text -> text.contains("sign in again"));
        Assertions.assertEquals(ClientState.SIGNED_OUT, player.client.getState());
        Assertions.assertFalse(white.isConnected());
        Assertions.assertFalse(games.getGame(gameID).game().isGameOver());
    }

    private RecordingObserver watchFromAnotherConnection() throws Exception {
        RecordingObserver audience = new RecordingObserver();
        facade(port).connectGame(blackAuth.authToken(), gameID, audience);
        audience.take(LoadGameMessage.class);
        return audience;
    }

    private LiveClient enter(String username) throws Exception {
        ServerFacade facade = switch (username) {
            case "white" -> white;
            case "black" -> black;
            default -> observer;
        };
        ChessClient client = new ChessClient(facade);
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        client.setNotificationListener(messages::add);
        client.evaluate("login " + username + " password");
        client.evaluate("list");
        client.evaluate(username.equals("observer") ? "observe 1" : "play 1 " + username);
        LiveClient participant = new LiveClient(client, facade, messages);
        await(participant, text -> text.startsWith("Joined") || text.startsWith("Observing"));
        return participant;
    }

    private String await(LiveClient player, Predicate<String> matches) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            String text = player.messages.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (text != null && matches.test(text)) {
                return text;
            }
        }
        return Assertions.fail("Expected gameplay display was not received.");
    }

    private record LiveClient(ChessClient client, ServerFacade facade, BlockingQueue<String> messages) { }
}
