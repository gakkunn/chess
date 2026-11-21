package client;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.glassfish.tyrus.client.ClientManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import websocket.WebSocketMessageCodec;
import websocket.commands.UserGameCommand;
import websocket.messages.ErrorMessage;
import websocket.messages.NotificationMessage;
import websocket.messages.ServerMessage;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WebSocketCommunicatorTests {

    private static final UserGameCommand CONNECT = new UserGameCommand(
            UserGameCommand.CommandType.CONNECT, "token", 1);

    @ParameterizedTest
    @CsvSource({
            "http://localhost:8080, ws://localhost:8080/ws",
            "https://example.com/path?query=value, wss://example.com/ws",
            "https://example.com:8443/path#fragment, wss://example.com:8443/ws",
            "http://[::1]:8080/path, ws://[::1]:8080/ws"
    })
    public void serverAddressesUseTheCorrectWebSocketSchemeAndRootEndpoint(String http, String expected) {
        Assertions.assertEquals(URI.create(expected), WebSocketCommunicator.webSocketUri(URI.create(http)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"io", "timeout", "runtime"})
    public void failedSendsReportCheckedErrorsAndReleaseTheOwnedTransport(String failure) throws Exception {
        StubClient client = new StubClient();
        GameplayFacadeTestSupport.RecordingObserver observer = new GameplayFacadeTestSupport.RecordingObserver();
        try (WebSocketCommunicator communicator = communicator(client)) {
            communicator.connect(CONNECT, observer);
            switch (failure) {
                case "io" -> client.writeResult = CompletableFuture.failedFuture(new IOException("Lost connection."));
                case "timeout" -> client.writeResult = new CompletableFuture<>() {
                    @Override
                    public Void get(long timeout, TimeUnit unit) throws TimeoutException {
                        throw new TimeoutException("Send timed out.");
                    }
                };
                default -> client.writeFailure = new IllegalStateException("Closed connection.");
            }

            Assertions.assertThrows(ServerFacadeException.class, () -> communicator.send(CONNECT));

            Assertions.assertFalse(communicator.isConnected());
            Assertions.assertFalse(client.open);
            Assertions.assertEquals(1, client.shutdowns.get());
            Assertions.assertEquals(1, observer.disconnectCount.get());
        }
        Assertions.assertEquals(1, client.shutdowns.get());
    }

    @Test
    public void interruptedSendsRestoreTheInterruptFlag() throws Exception {
        StubClient client = new StubClient();
        try (WebSocketCommunicator communicator = communicator(client)) {
            communicator.connect(CONNECT, message -> {});
            client.writeResult = new CompletableFuture<>();
            try {
                Thread.currentThread().interrupt();

                Assertions.assertThrows(ServerFacadeException.class, () -> communicator.send(CONNECT));

                Assertions.assertTrue(Thread.currentThread().isInterrupted());
                Assertions.assertFalse(communicator.isConnected());
                Assertions.assertEquals(1, client.shutdowns.get());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    public void aFailedSendDoesNotDeadlockAReceivingObserverThatAlsoSends() throws Exception {
        StubClient client = new StubClient();
        WebSocketCommunicator communicator = communicator(client);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch callbackEntered = new CountDownLatch(1);
        GameplayFacadeTestSupport.RecordingObserver observer = new GameplayFacadeTestSupport.RecordingObserver() {
            @Override
            public void notify(ServerMessage message) {
                callbackEntered.countDown();
                try {
                    communicator.send(CONNECT);
                } catch (ServerFacadeException expected) {
                    messages.add(new ErrorMessage(expected.getMessage()));
                }
            }
        };
        var executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
        Future<?> sender = null;
        Future<?> receiver = null;
        try {
            communicator.connect(CONNECT, observer);
            CompletableFuture<Void> write = new CompletableFuture<>() {
                @Override
                public Void get(long timeout, TimeUnit unit)
                        throws InterruptedException, ExecutionException, TimeoutException {
                    sending.countDown();
                    return super.get(timeout, unit);
                }
            };
            client.writeResult = write;
            sender = executor.submit(() -> {
                communicator.send(CONNECT);
                return null;
            });
            Assertions.assertTrue(sending.await(3, TimeUnit.SECONDS));
            receiver = executor.submit(() -> client.emit("reply with a command"));
            Assertions.assertTrue(callbackEntered.await(3, TimeUnit.SECONDS));

            write.completeExceptionally(new IOException("Write failed."));

            Future<?> failedSender = sender;
            Assertions.assertThrows(ExecutionException.class, () -> failedSender.get(3, TimeUnit.SECONDS));
            receiver.get(3, TimeUnit.SECONDS);
            Assertions.assertFalse(communicator.isConnected());
            Assertions.assertEquals(1, client.shutdowns.get());
            Assertions.assertEquals(1, observer.disconnectCount.get());
        } finally {
            executor.shutdownNow();
            if ((sender == null || sender.isDone()) && (receiver == null || receiver.isDone())) {
                communicator.close();
            }
        }
    }

    @Test
    public void cancellationDuringHandshakeClosesTheLateSessionAndIgnoresOldCallbacks() throws Exception {
        StubClient oldClient = new StubClient();
        oldClient.blockHandshake = true;
        StubClient newClient = new StubClient();
        GameplayFacadeTestSupport.RecordingObserver oldObserver = new GameplayFacadeTestSupport.RecordingObserver();
        GameplayFacadeTestSupport.RecordingObserver newObserver = new GameplayFacadeTestSupport.RecordingObserver();
        try (var executor = Executors.newSingleThreadExecutor();
             WebSocketCommunicator communicator = communicator(oldClient, newClient)) {
            Future<?> connecting = executor.submit(() -> {
                communicator.connect(CONNECT, oldObserver);
                return null;
            });
            try {
                Assertions.assertTrue(oldClient.handshakeStarted.await(3, TimeUnit.SECONDS));
                communicator.disconnect();
            } finally {
                oldClient.allowHandshake.countDown();
            }
            ExecutionException cancellation = Assertions.assertThrows(ExecutionException.class,
                    () -> connecting.get(3, TimeUnit.SECONDS));
            Assertions.assertInstanceOf(ServerFacadeException.class, cancellation.getCause());
            Assertions.assertFalse(oldClient.open);
            Assertions.assertEquals(1, oldClient.shutdowns.get());

            communicator.connect(CONNECT, newObserver);
            oldClient.remoteClose();
            oldClient.endpoint.onError(oldClient.session, new IOException("Late error."));
            oldClient.emit("old message");
            newClient.emit("current message");

            Assertions.assertEquals("current message", newObserver.take(NotificationMessage.class).getMessage());
            oldObserver.assertQuiet();
            Assertions.assertEquals(0, oldObserver.disconnectCount.get());
            Assertions.assertEquals(0, newObserver.disconnectCount.get());
            Assertions.assertTrue(communicator.isConnected());
        }
    }

    @Test
    public void aDisconnectCallbackCanReconnectBeforeTheOldCallbackReturns() throws Exception {
        StubClient oldClient = new StubClient();
        StubClient newClient = new StubClient();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        GameplayFacadeTestSupport.RecordingObserver newObserver = new GameplayFacadeTestSupport.RecordingObserver();
        try (WebSocketCommunicator communicator = communicator(oldClient, newClient)) {
            GameplayFacadeTestSupport.RecordingObserver oldObserver = new GameplayFacadeTestSupport.RecordingObserver() {
                @Override
                public void onDisconnect() {
                    super.onDisconnect();
                    try {
                        communicator.connect(CONNECT, newObserver);
                    } catch (ServerFacadeException exception) {
                        failure.set(exception);
                    }
                }
            };
            communicator.connect(CONNECT, oldObserver);

            oldClient.remoteClose();

            Assertions.assertNull(failure.get());
            Assertions.assertTrue(communicator.isConnected());
            Assertions.assertEquals(1, oldObserver.disconnectCount.get());
            oldObserver.take(ErrorMessage.class);
            oldClient.endpoint.onError(oldClient.session, new IOException("Late error."));
            newClient.emit("reconnected");
            Assertions.assertEquals("reconnected", newObserver.take(NotificationMessage.class).getMessage());
            Assertions.assertEquals(1, oldClient.shutdowns.get());
        }
    }

    @Test
    public void malformedMessagesAreReportedAndDoNotStopSubsequentDelivery() throws Exception {
        StubClient client = new StubClient();
        GameplayFacadeTestSupport.RecordingObserver observer = new GameplayFacadeTestSupport.RecordingObserver();
        try (WebSocketCommunicator communicator = communicator(client)) {
            communicator.connect(CONNECT, observer);

            client.listener.onMessage("not-json");
            observer.take(ErrorMessage.class);
            client.listener.onMessage("{\"serverMessageType\":\"LOAD_GAME\",\"game\":{}}");
            observer.take(ErrorMessage.class);
            client.emit("valid message");

            Assertions.assertEquals("valid message", observer.take(NotificationMessage.class).getMessage());
            Assertions.assertTrue(communicator.isConnected());
            Assertions.assertEquals(0, observer.disconnectCount.get());
        }
    }

    @Test
    public void throwingObserversDoNotBreakDeliveryOrResourceCleanup() throws Exception {
        StubClient client = new StubClient();
        AtomicInteger delivered = new AtomicInteger();
        ServerMessageObserver observer = new ServerMessageObserver() {
            @Override
            public void notify(ServerMessage message) {
                if (delivered.incrementAndGet() == 1) {
                    throw new IllegalStateException("Test observer failed.");
                }
            }

            @Override
            public void onDisconnect() {
                throw new IllegalStateException("Test disconnect observer failed.");
            }
        };
        try (WebSocketCommunicator communicator = communicator(client)) {
            communicator.connect(CONNECT, observer);
            client.emit("first");
            client.emit("second");
            Assertions.assertEquals(2, delivered.get());
            client.remoteClose();
            Assertions.assertFalse(communicator.isConnected());
            Assertions.assertEquals(1, client.shutdowns.get());
        }
    }

    private WebSocketCommunicator communicator(StubClient... clients) {
        Queue<StubClient> available = new ArrayDeque<>(List.of(clients));
        return new WebSocketCommunicator(URI.create("http://localhost:8080"), available::remove);
    }

    private static final class StubClient extends ClientManager {
        private volatile boolean open = true;
        private boolean blockHandshake;
        private final CountDownLatch handshakeStarted = new CountDownLatch(1);
        private final CountDownLatch allowHandshake = new CountDownLatch(1);
        private final AtomicInteger shutdowns = new AtomicInteger();
        private Future<Void> writeResult = CompletableFuture.completedFuture(null);
        private RuntimeException writeFailure;
        private Endpoint endpoint;
        private MessageHandler.Whole<String> listener;
        private final Session session;

        @SuppressWarnings("unchecked")
        private StubClient() {
            RemoteEndpoint.Async remote = (RemoteEndpoint.Async) Proxy.newProxyInstance(
                    RemoteEndpoint.Async.class.getClassLoader(), new Class<?>[]{RemoteEndpoint.Async.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("sendText")) {
                            if (writeFailure != null) {
                                throw writeFailure;
                            }
                            return writeResult;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "isOpen" -> open;
                        case "getAsyncRemote" -> remote;
                        case "addMessageHandler" -> {
                            listener = (MessageHandler.Whole<String>) arguments[1];
                            yield null;
                        }
                        case "close" -> {
                            open = false;
                            endpoint.onClose((Session) proxy,
                                    new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "closed"));
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public Session connectToServer(Endpoint endpoint, ClientEndpointConfig config, URI uri) throws IOException {
            this.endpoint = endpoint;
            handshakeStarted.countDown();
            if (blockHandshake) {
                try {
                    if (!allowHandshake.await(3, TimeUnit.SECONDS)) {
                        throw new IOException("Test handshake timed out.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException(exception);
                }
            }
            endpoint.onOpen(session, config);
            return session;
        }

        @Override
        public void shutdown() {
            shutdowns.incrementAndGet();
        }

        private void emit(String text) {
            listener.onMessage(new WebSocketMessageCodec().serialize(new NotificationMessage(text)));
        }

        private void remoteClose() {
            open = false;
            endpoint.onClose(session, new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "server closed"));
        }
    }
}
