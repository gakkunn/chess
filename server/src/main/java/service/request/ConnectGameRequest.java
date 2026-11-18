package service.request;

public record ConnectGameRequest(String authToken, Integer gameID) {
}
