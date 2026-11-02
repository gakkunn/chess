package client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.Server;

import java.io.IOException;
import java.net.ServerSocket;

public class ServerFacadeTests {

    private static Server server;
    private static ServerFacade facade;

    @BeforeAll
    public static void init() {
        server = new Server();
        var port = server.run(0);
        System.out.println("Started test HTTP server on " + port);
        facade = new ServerFacade(port);
    }

    @BeforeEach
    void clearDatabase() throws ServerFacadeException {
        facade.clear();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void clearSucceedsWhenServerIsAvailable() {
        Assertions.assertDoesNotThrow(facade::clear);
    }

    @Test
    void clearReportsWhenServerIsUnavailable() throws IOException {
        int unavailablePort = findUnavailablePort();
        ServerFacade unavailableFacade = new ServerFacade(unavailablePort);

        ServerFacadeException exception = Assertions.assertThrows(
                ServerFacadeException.class,
                unavailableFacade::clear);

        Assertions.assertEquals(
                "Unable to reach the chess server.",
                exception.getMessage());
    }

    private int findUnavailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
