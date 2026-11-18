package server.websocket;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import websocket.WebSocketMessageCodec;
import websocket.messages.NotificationMessage;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class ConnectionManagerTests {

    @Test
    public void broadcastsReachOnlyOtherConnectionsInTheSameGame() {
        ConnectionManager manager = new ConnectionManager();
        TestSession sender = new TestSession();
        TestSession recipient = new TestSession();
        TestSession otherGame = new TestSession();
        manager.add(sender.session, "sender-token", 1);
        manager.add(recipient.session, "recipient-token", 1);
        manager.add(otherGame.session, "other-token", 2);

        manager.broadcast(1, new NotificationMessage("Alice connected as WHITE."), sender.session);

        Assertions.assertTrue(sender.messages.isEmpty());
        Assertions.assertTrue(otherGame.messages.isEmpty());
        Assertions.assertEquals(1, recipient.messages.size());
        NotificationMessage message = Assertions.assertInstanceOf(NotificationMessage.class,
                new WebSocketMessageCodec().deserializeMessage(recipient.messages.getFirst()));
        Assertions.assertEquals("Alice connected as WHITE.", message.getMessage());
    }

    @Test
    public void existingConnectionCannotBeReboundToAnotherGameOrIdentity() {
        ConnectionManager manager = new ConnectionManager();
        TestSession client = new TestSession();

        Assertions.assertTrue(manager.add(client.session, "original-token", 1));
        Assertions.assertFalse(manager.add(client.session, "another-token", 2));
        Assertions.assertTrue(manager.contains(client.session, "original-token", 1));
        Assertions.assertFalse(manager.contains(client.session, "another-token", 2));

        manager.remove(client.session);
        manager.broadcast(1, new NotificationMessage("No longer subscribed."), null);
        Assertions.assertTrue(client.messages.isEmpty());
    }

    @Test
    public void closedAndFailedConnectionsDoNotPreventDeliveryToOthers() {
        ConnectionManager manager = new ConnectionManager();
        TestSession closed = new TestSession();
        TestSession failed = new TestSession();
        TestSession healthy = new TestSession();
        closed.open = false;
        failed.failSends = true;
        manager.add(closed.session, "closed-token", 1);
        manager.add(failed.session, "failed-token", 1);
        manager.add(healthy.session, "healthy-token", 1);

        manager.broadcast(1, new NotificationMessage("Still delivered."), null);

        Assertions.assertEquals(1, healthy.messages.size());
        Assertions.assertFalse(manager.contains(closed.session, "closed-token", 1));
        Assertions.assertFalse(manager.contains(failed.session, "failed-token", 1));
        Assertions.assertTrue(manager.contains(healthy.session, "healthy-token", 1));
    }

    @Test
    public void clearingClosesConnectionsAndRemovesSubscriptions() {
        ConnectionManager manager = new ConnectionManager();
        TestSession first = new TestSession();
        TestSession second = new TestSession();
        manager.add(first.session, "first-token", 1);
        manager.add(second.session, "second-token", 2);

        manager.clear();

        Assertions.assertFalse(first.open);
        Assertions.assertFalse(second.open);
        Assertions.assertFalse(manager.contains(first.session, "first-token", 1));
        Assertions.assertFalse(manager.contains(second.session, "second-token", 2));
    }

    private static final class TestSession {
        private final List<String> messages = new ArrayList<>();
        private boolean open = true;
        private boolean failSends;
        private final Session session;

        private TestSession() {
            RemoteEndpoint remote = (RemoteEndpoint) Proxy.newProxyInstance(
                    RemoteEndpoint.class.getClassLoader(), new Class<?>[]{RemoteEndpoint.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("sendString")) {
                            if (failSends) {
                                throw new IOException("Connection failed.");
                            }
                            messages.add((String) arguments[0]);
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            session = (Session) Proxy.newProxyInstance(
                    Session.class.getClassLoader(), new Class<?>[]{Session.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "isOpen" -> open;
                        case "getRemote" -> remote;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        case "close" -> {
                            open = false;
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
