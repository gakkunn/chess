package service.request;

public record LeaveGameRequest(String authToken, Integer gameID) {
}
