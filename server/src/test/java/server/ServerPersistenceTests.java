package server;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.result.CreateGameResult;
import service.result.GameSummary;
import service.result.ListGamesResult;
import service.result.LoginResult;
import service.result.RegisterResult;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public class ServerPersistenceTests extends EndpointTestSupport {

    @Test
    public void defaultServerPreservesApplicationDataAcrossRestart()
            throws IOException, InterruptedException {
        Server firstServer = new Server();
        URI firstServerUri = startServer(firstServer);
        RegisterResult registeredUser = registerSuccessfully(
                firstServerUri,
                "persistent-user",
                "persistent-user@example.com");
        CreateGameResult createdGame = createGameSuccessfully(
                firstServerUri,
                registeredUser.authToken(),
                "Persistent Game");

        stopServer(firstServer);

        Server restartedServer = new Server();
        URI restartedServerUri = startServer(restartedServer);
        ListGamesResult gamesAfterRestart = listGamesSuccessfully(
                restartedServerUri,
                registeredUser.authToken());
        LoginResult loginAfterRestart = loginSuccessfully(
                restartedServerUri,
                registeredUser.username());
        List<GameSummary> expectedGames = List.of(new GameSummary(
                createdGame.gameID(),
                null,
                null,
                "Persistent Game"));

        Assertions.assertEquals(expectedGames, gamesAfterRestart.games());
        Assertions.assertEquals(
                registeredUser.username(),
                loginAfterRestart.username());
        Assertions.assertNotNull(loginAfterRestart.authToken());
    }
}
