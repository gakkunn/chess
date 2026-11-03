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

    public AuthData login(String username, String password)
            throws ServerFacadeException {
        LoginRequest request = new LoginRequest(username, password);

        return communicator.sendRequest(
                "POST",
                "/session",
                request,
                null,
                AuthData.class);
    }

    public void logout(String authToken) throws ServerFacadeException {
        communicator.sendRequest(
                "DELETE",
                "/session",
                null,
                authToken,
                Void.class);
    }

    public int createGame(String authToken, String gameName)
            throws ServerFacadeException {
        CreateGameRequest request = new CreateGameRequest(gameName);

        CreateGameResponse response = communicator.sendRequest(
                "POST",
                "/game",
                request,
                authToken,
                CreateGameResponse.class);

        return response.gameID();
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

    private record LoginRequest(String username, String password) {
    }

    private record CreateGameRequest(String gameName) {
    }

    private record CreateGameResponse(int gameID) {
    }
}
