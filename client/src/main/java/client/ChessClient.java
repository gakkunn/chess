package client;

import java.util.Locale;
import java.util.Objects;

public class ChessClient {

    private static final String PRELOGIN_HELP = String.join(
            System.lineSeparator(),
            "Available commands:",
            "  register <username> <password> <email> - Create an account",
            "  login <username> <password> - Sign in",
            "  help - Show available commands",
            "  quit - Exit the chess client");

    private static final String POSTLOGIN_HELP = String.join(
            System.lineSeparator(),
            "Available commands:",
            "  create <name> - Create a game",
            "  list - List available games",
            "  play <number> <WHITE|BLACK> - Join a game",
            "  observe <number> - Observe a game",
            "  logout - Sign out",
            "  help - Show available commands",
            "  quit - Exit the chess client");

    private final ServerFacade serverFacade;
    private ClientState state;

    public ChessClient(String serverUrl) {
        this(new ServerFacade(serverUrl));
    }

    public ChessClient(ServerFacade serverFacade) {
        this.serverFacade = Objects.requireNonNull(
                serverFacade,
                "Server facade cannot be null.");
        state = ClientState.SIGNED_OUT;
    }

    public ClientState getState() {
        return state;
    }

    public String evaluate(String input) {
        if (state == ClientState.QUIT) {
            return "The chess client is closed.";
        }

        if (input == null || input.isBlank()) {
            return "Please enter a command. Type 'help' to see available commands.";
        }

        String[] tokens = input.trim().split("\\s+");
        String command = tokens[0].toLowerCase(Locale.ROOT);

        return switch (command) {
            case "help" -> showHelp(tokens.length);
            case "quit" -> quit(tokens.length);
            default -> "Unknown command. Type 'help' to see available commands.";
        };
    }

    private String showHelp(int tokenCount) {
        if (tokenCount != 1) {
            return "Usage: help";
        }

        if (state == ClientState.SIGNED_IN) {
            return POSTLOGIN_HELP;
        }

        return PRELOGIN_HELP;
    }

    private String quit(int tokenCount) {
        if (tokenCount != 1) {
            return "Usage: quit";
        }

        state = ClientState.QUIT;
        return "Goodbye.";
    }
}
