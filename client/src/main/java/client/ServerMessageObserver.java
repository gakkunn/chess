package client;

import websocket.messages.ServerMessage;

@FunctionalInterface
public interface ServerMessageObserver {

    void notify(ServerMessage message);

    /**
     * Reports an unexpected disconnect or failed send; explicit disconnects are silent.
     */
    default void onDisconnect() {
    }
}
