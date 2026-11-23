package ui;

import client.ChessClient;
import client.ClientState;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.Scanner;

public class Repl {

    private final ChessClient client;
    private final Scanner scanner;
    private final PrintStream output;
    private final Object outputLock = new Object();
    private final Queue<String> pendingMessages = new ArrayDeque<>();
    private boolean processingCommand;
    private boolean waitingForInput;
    private boolean running;

    public Repl(String serverUrl) {
        this(new ChessClient(serverUrl), new Scanner(System.in), System.out);
    }

    Repl(ChessClient client, Scanner scanner, PrintStream output) {
        this.client = Objects.requireNonNull(client, "Chess client cannot be null.");
        this.scanner = Objects.requireNonNull(scanner, "Input scanner cannot be null.");
        this.output = Objects.requireNonNull(output, "Output stream cannot be null.");
    }

    public void run() {
        start();
        try {
            while (client.getState() != ClientState.QUIT) {
                showPrompt();
                if (!scanner.hasNextLine()) {
                    synchronized (outputLock) {
                        output.println();
                    }
                    return;
                }
                evaluateInput(scanner.nextLine());
            }
        } finally {
            finish();
        }
    }

    private void start() {
        synchronized (outputLock) {
            running = true;
            client.setNotificationListener(this::displayNotification);
            output.println("Welcome to 240 Chess. Type 'help' to get started.");
        }
    }

    private void showPrompt() {
        synchronized (outputLock) {
            waitingForInput = true;
            output.print(prompt());
            output.flush();
        }
    }

    private void evaluateInput(String input) {
        synchronized (outputLock) {
            waitingForInput = false;
            processingCommand = true;
        }
        String response = client.evaluate(input);
        synchronized (outputLock) {
            if (!response.isBlank()) {
                output.println(response);
            }
            processingCommand = false;
            if (client.getState() == ClientState.QUIT) {
                pendingMessages.clear();
            } else {
                while (!pendingMessages.isEmpty()) {
                    output.println(pendingMessages.remove());
                }
            }
            output.flush();
        }
    }

    private void finish() {
        synchronized (outputLock) {
            running = false;
            pendingMessages.clear();
        }
        client.setNotificationListener(message -> { });
        client.close();
    }

    private void displayNotification(String message) {
        synchronized (outputLock) {
            if (!running || client.getState() == ClientState.QUIT) {
                return;
            }
            if (processingCommand) {
                pendingMessages.add(message);
                return;
            }
            if (waitingForInput) {
                output.println();
            }
            output.println(message);
            if (waitingForInput) {
                output.print(prompt());
            }
            output.flush();
        }
    }

    private String prompt() {
        return switch (client.getState()) {
            case SIGNED_OUT -> "[SIGNED_OUT] >>> ";
            case SIGNED_IN -> "[SIGNED_IN] >>> ";
            case CONNECTING -> "[CONNECTING] >>> ";
            case IN_GAME -> "[IN_GAME] >>> ";
            case QUIT -> "";
        };
    }
}
