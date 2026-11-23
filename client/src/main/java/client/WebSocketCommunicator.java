package client;

import com.google.gson.JsonParseException;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import websocket.WebSocketMessageCodec;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.ServerMessage;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class WebSocketCommunicator implements AutoCloseable {

    private static final int TIMEOUT_MILLIS = 5000;
    private static final System.Logger LOGGER = System.getLogger(WebSocketCommunicator.class.getName());

    private final URI endpoint;
    private final Supplier<ClientManager> clientFactory;
    private final WebSocketMessageCodec codec = new WebSocketMessageCodec();
    private final AtomicReference<Connection> active = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    WebSocketCommunicator(URI serverUri) {
        this(serverUri, ClientManager::createClient);
    }

    WebSocketCommunicator(URI serverUri, Supplier<ClientManager> clientFactory) {
        endpoint = webSocketUri(serverUri);
        this.clientFactory = Objects.requireNonNull(clientFactory);
    }

    void connect(UserGameCommand command, ServerMessageObserver observer) throws ServerFacadeException {
        Objects.requireNonNull(observer, "Server message observer cannot be null.");
        requireNotClosed();

        Connection connection = new Connection(observer);
        if (!active.compareAndSet(null, connection)) {
            connection.opening = false;
            connection.dispose();
            throw new ServerFacadeException("A game connection is already open or connecting.");
        }

        try {
            requireNotClosed();
            open(connection);
            send(connection, command);
        } catch (DeploymentException | IOException | RuntimeException exception) {
            cancel(connection);
            throw new ServerFacadeException("Unable to open a game connection to the chess server.", exception);
        } catch (ServerFacadeException exception) {
            cancel(connection);
            throw exception;
        }
    }

    void send(UserGameCommand command) throws ServerFacadeException {
        Connection connection = active.get();
        if (connection == null) {
            throw new ServerFacadeException("No game connection is open.");
        }
        send(connection, command);
    }

    boolean isConnected() {
        Connection connection = active.get();
        return connection != null && connection.isUsable();
    }

    void disconnect() {
        Connection connection = active.getAndSet(null);
        if (connection != null) {
            connection.dispose();
        }
    }

    @Override
    public void close() {
        closed.set(true);
        disconnect();
    }

    static URI webSocketUri(URI serverUri) {
        String scheme = "https".equalsIgnoreCase(serverUri.getScheme()) ? "wss" : "ws";
        try {
            return new URI(scheme, null, serverUri.getHost(), serverUri.getPort(), "/ws", null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Unable to create the game WebSocket URL.", exception);
        }
    }

    private void requireNotClosed() throws ServerFacadeException {
        if (closed.get()) {
            throw new ServerFacadeException("The chess client is closed.");
        }
    }

    /**
     * Completes the handshake. A failed open is detached before callbacks can report it as a lost connection.
     */
    private void open(Connection connection) throws DeploymentException, IOException {
        boolean opened = false;
        try {
            connection.client.connectToServer(connection, ClientEndpointConfig.Builder.create().build(), endpoint);
            opened = true;
        } finally {
            if (!opened) {
                active.compareAndSet(connection, null);
                connection.closeRequested.set(true);
            }
            connection.opening = false;
            if (connection.closeRequested.get()) {
                connection.dispose();
            }
        }
    }

    private void send(Connection connection, UserGameCommand command) throws ServerFacadeException {
        try {
            synchronized (connection.sendLock) {
                if (!connection.isUsable()) {
                    throw new ServerFacadeException("No game connection is open.");
                }

                connection.session.getAsyncRemote().sendText(codec.serialize(command))
                        .get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            connection.finish(null);
            throw new ServerFacadeException("Sending the game command was interrupted.", exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            connection.finish(null);
            throw new ServerFacadeException("Unable to send the game command to the chess server.", exception);
        }
    }

    private void cancel(Connection connection) {
        active.compareAndSet(connection, null);
        connection.opening = false;
        connection.dispose();
    }

    private final class Connection extends Endpoint {
        private final ClientManager client = clientFactory.get();
        private final ServerMessageObserver observer;
        private final Object sendLock = new Object();
        private final Object deliveryLock = new Object();
        private final AtomicBoolean closeRequested = new AtomicBoolean();
        private final AtomicBoolean resourcesReleased = new AtomicBoolean();
        private volatile Session session;
        private volatile boolean opening = true;

        private Connection(ServerMessageObserver observer) {
            this.observer = observer;
            client.getProperties().put(ClientProperties.HANDSHAKE_TIMEOUT, TIMEOUT_MILLIS);
            client.setAsyncSendTimeout(TIMEOUT_MILLIS);
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            this.session = session;
            session.addMessageHandler(String.class, this::receive);
            if (active.get() != this || closeRequested.get()) {
                dispose();
            }
        }

        @Override
        public void onClose(Session session, CloseReason reason) {
            finish("The game connection to the chess server was closed.");
        }

        @Override
        public void onError(Session session, Throwable error) {
            finish("The game connection to the chess server was lost.");
        }

        private boolean isUsable() {
            Session current = session;
            return active.get() == this && !opening && !closeRequested.get()
                    && current != null && current.isOpen();
        }

        private void receive(String json) {
            ServerMessage message;
            try {
                message = codec.deserializeMessage(json);
            } catch (JsonParseException exception) {
                message = new ErrorMessage("The chess server sent an unreadable game message.");
            }

            synchronized (deliveryLock) {
                if (active.get() == this && !closeRequested.get()) {
                    notifyObserver(message);
                }
            }
        }

        private void finish(String errorMessage) {
            if (!active.compareAndSet(this, null)) {
                return;
            }

            try {
                synchronized (deliveryLock) {
                    if (!opening) {
                        if (errorMessage != null) {
                            notifyObserver(new ErrorMessage(errorMessage));
                        }
                        try {
                            observer.onDisconnect();
                        } catch (RuntimeException exception) {
                            LOGGER.log(System.Logger.Level.WARNING, "Unable to handle the game disconnect.", exception);
                        }
                    }
                }
            } finally {
                dispose();
            }
        }

        private void notifyObserver(ServerMessage message) {
            try {
                observer.notify(message);
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Unable to handle a chess server message.", exception);
            }
        }

        private void dispose() {
            closeRequested.set(true);
            Session current;
            synchronized (deliveryLock) {
                current = session;
            }
            try {
                if (current != null && current.isOpen()) {
                    current.close();
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.log(System.Logger.Level.DEBUG, "Unable to close the game connection.", exception);
            } finally {
                if (!opening && resourcesReleased.compareAndSet(false, true)) {
                    client.shutdown();
                }
            }
        }
    }
}
