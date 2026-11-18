package server.websocket;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import websocket.WebSocketMessageCodec;
import websocket.messages.ServerMessage;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionManager.class);

    private final Map<Session, Connection> connections = new ConcurrentHashMap<>();
    private final WebSocketMessageCodec codec = new WebSocketMessageCodec();

    public boolean add(Session session, String authToken, int gameID) {
        return connections.putIfAbsent(session, new Connection(authToken, gameID)) == null;
    }

    public boolean contains(Session session, String authToken, int gameID) {
        return new Connection(authToken, gameID).equals(connections.get(session));
    }

    public void remove(Session session) {
        connections.remove(session);
    }

    public boolean send(Session session, ServerMessage message) {
        return send(session, codec.serialize(message));
    }

    public void broadcast(int gameID, ServerMessage message, Session excludedSession) {
        String json = codec.serialize(message);

        for (Map.Entry<Session, Connection> entry : connections.entrySet()) {
            if (entry.getValue().gameID() == gameID
                    && !entry.getKey().equals(excludedSession)) {
                send(entry.getKey(), json);
            }
        }
    }

    public void clear() {
        Session[] sessions = connections.keySet().toArray(Session[]::new);
        connections.clear();

        for (Session session : sessions) {
            try {
                session.close(1001, "Server data cleared.");
            } catch (RuntimeException exception) {
                LOGGER.debug("Unable to close a WebSocket connection.", exception);
            }
        }
    }

    private boolean send(Session session, String json) {
        synchronized (session) {
            if (!session.isOpen()) {
                remove(session);
                return false;
            }

            try {
                session.getRemote().sendString(json);
                return true;
            } catch (IOException | RuntimeException exception) {
                remove(session);
                return false;
            }
        }
    }

    private record Connection(String authToken, int gameID) {
    }
}
