package client;

import chess.ChessGame;
import chess.ChessMove;
import model.AuthData;
import websocket.commands.MakeMoveCommand;
import websocket.commands.UserGameCommand;

import java.util.List;

public class ServerFacade implements AutoCloseable {

    private final HttpCommunicator communicator;
    private final WebSocketCommunicator webSocket;

    public ServerFacade(int port) {
        this(createLocalServerUrl(port));
    }

    public ServerFacade(String serverUrl) {
        communicator = new HttpCommunicator(serverUrl);
        webSocket = new WebSocketCommunicator(communicator.serverUri());
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

    public List<GameSummary> listGames(String authToken)
            throws ServerFacadeException {
        ListGamesResponse response = communicator.sendRequest(
                "GET",
                "/game",
                null,
                authToken,
                ListGamesResponse.class);

        if (response.games() == null) {
            throw new ServerFacadeException(
                    "The chess server returned an invalid game list.");
        }

        return List.copyOf(response.games());
    }

    public void joinGame(
            String authToken,
            ChessGame.TeamColor playerColor,
            int gameID) throws ServerFacadeException {
        JoinGameRequest request = new JoinGameRequest(playerColor, gameID);

        communicator.sendRequest(
                "PUT",
                "/game",
                request,
                authToken,
                Void.class);
    }

    /**
     * Opens the game connection. The board or server rejection arrives through the observer.
     */
    public void connectGame(String authToken, int gameID, ServerMessageObserver observer)
            throws ServerFacadeException {
        webSocket.connect(new UserGameCommand(UserGameCommand.CommandType.CONNECT, authToken, gameID), observer);
    }

    public void makeMove(String authToken, int gameID, ChessMove move) throws ServerFacadeException {
        webSocket.send(new MakeMoveCommand(authToken, gameID, move));
    }

    public void leaveGame(String authToken, int gameID) throws ServerFacadeException {
        webSocket.send(new UserGameCommand(UserGameCommand.CommandType.LEAVE, authToken, gameID));
    }

    public void resignGame(String authToken, int gameID) throws ServerFacadeException {
        webSocket.send(new UserGameCommand(UserGameCommand.CommandType.RESIGN, authToken, gameID));
    }

    /**
     * Reports the WebSocket transport state, independently of game authorization.
     */
    public boolean isConnected() {
        return webSocket.isConnected();
    }

    public void disconnect() {
        webSocket.disconnect();
    }

    @Override
    public void close() {
        webSocket.close();
        communicator.close();
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

    private record ListGamesResponse(List<GameSummary> games) {
    }

    private record JoinGameRequest(
            ChessGame.TeamColor playerColor,
            int gameID) {
    }
}
