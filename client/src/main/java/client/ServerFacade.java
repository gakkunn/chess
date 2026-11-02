package client;

import model.AuthData;

public class ServerFacade {

    private final HttpCommunicator communicator;

    public ServerFacade(int port) {
        this(createLocalServerUrl(port));
    }

    public ServerFacade(String serverUrl) {
        communicator = new HttpCommunicator(serverUrl);
    }

    public void clear() throws ServerFacadeException {
        communicator.sendRequest(
                "DELETE",
                "/db",
                null,
                null,
                Void.class);
    }

    public AuthData register(String username, String password, String email)
            throws ServerFacadeException {
        RegisterRequest request = new RegisterRequest(
                username,
                password,
                email);

        return communicator.sendRequest(
                "POST",
                "/user",
                request,
                null,
                AuthData.class);
    }

    private static String createLocalServerUrl(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "Server port must be between 1 and 65535.");
        }

        return "http://localhost:" + port;
    }

    private record RegisterRequest(
            String username,
            String password,
            String email) {
    }
}
