package ui;

import client.ChessClient;
import client.ClientState;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Scanner;

public class Repl {

    private final ChessClient client;
    private final Scanner scanner;
    private final PrintStream output;

    public Repl(String serverUrl) {
        this(new ChessClient(serverUrl), new Scanner(System.in), System.out);
    }

    Repl(ChessClient client, Scanner scanner, PrintStream output) {
        this.client = Objects.requireNonNull(client, "Chess client cannot be null.");
        this.scanner = Objects.requireNonNull(scanner, "Input scanner cannot be null.");
        this.output = Objects.requireNonNull(output, "Output stream cannot be null.");
    }

    public void run() {
        output.println("Welcome to 240 Chess. Type 'help' to get started.");

        while (client.getState() != ClientState.QUIT) {
            output.print(prompt());
            output.flush();

            if (!scanner.hasNextLine()) {
                output.println();
                return;
            }

            String response = client.evaluate(scanner.nextLine());
            if (!response.isBlank()) {
                output.println(response);
            }
        }
    }

    private String prompt() {
        return switch (client.getState()) {
            case SIGNED_OUT -> "[SIGNED_OUT] >>> ";
            case SIGNED_IN -> "[SIGNED_IN] >>> ";
            case QUIT -> "";
        };
    }
}
