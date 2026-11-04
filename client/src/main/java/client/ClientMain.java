package client;

import ui.Repl;

public class ClientMain {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    public static void main(String[] args) {
        if (args.length > 1) {
            System.err.println("Usage: ClientMain [server-url]");
            return;
        }

        String serverUrl = args.length == 1 ? args[0] : DEFAULT_SERVER_URL;
        new Repl(serverUrl).run();
    }
}
