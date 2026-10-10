package server;

public class ServerMain {
    public static void main(String[] args) {
        int desiredPort = 8080;
        Server server = new Server();
        int actualPort = server.run(desiredPort);

        System.out.println("♕ 240 Chess Server running on port " + actualPort);
    }
}
