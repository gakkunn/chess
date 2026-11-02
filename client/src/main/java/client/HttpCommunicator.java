package client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class HttpCommunicator {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final URI serverUri;
    private final HttpClient httpClient;
    private final Gson serializer;

    HttpCommunicator(String serverUrl) {
        serverUri = createServerUri(serverUrl);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        serializer = new Gson();
    }

    <T> T sendRequest(
            String method,
            String path,
            Object requestBody,
            String authToken,
            Class<T> responseType) throws ServerFacadeException {
        HttpRequest request = buildRequest(method, path, requestBody, authToken);
        HttpResponse<String> response = send(request);

        if (!isSuccessful(response.statusCode())) {
            throw createResponseException(response.body());
        }

        return readResponseBody(response.body(), responseType);
    }

    private HttpRequest buildRequest(
            String method,
            String path,
            Object requestBody,
            String authToken) {
        HttpRequest.BodyPublisher bodyPublisher = createBodyPublisher(requestBody);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(serverUri.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .method(method, bodyPublisher);

        if (requestBody != null) {
            requestBuilder.header("Content-Type", "application/json");
        }

        if (authToken != null && !authToken.isBlank()) {
            requestBuilder.header("authorization", authToken);
        }

        return requestBuilder.build();
    }

    private HttpRequest.BodyPublisher createBodyPublisher(Object requestBody) {
        if (requestBody == null) {
            return HttpRequest.BodyPublishers.noBody();
        }

        String json = serializer.toJson(requestBody);
        return HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> send(HttpRequest request)
            throws ServerFacadeException {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServerFacadeException(
                    "The request to the chess server was interrupted.",
                    exception);
        } catch (IOException exception) {
            throw new ServerFacadeException(
                    "Unable to reach the chess server.",
                    exception);
        }
    }

    private boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private ServerFacadeException createResponseException(String responseBody) {
        try {
            ErrorResponse errorResponse = serializer.fromJson(
                    responseBody,
                    ErrorResponse.class);

            if (errorResponse != null
                    && errorResponse.message() != null
                    && !errorResponse.message().isBlank()) {
                return new ServerFacadeException(errorResponse.message());
            }
        } catch (JsonParseException exception) {
            return new ServerFacadeException(
                    "The chess server returned an unexpected response.",
                    exception);
        }

        return new ServerFacadeException(
                "The chess server could not complete the request.");
    }

    private <T> T readResponseBody(String responseBody, Class<T> responseType)
            throws ServerFacadeException {
        if (responseType == Void.class) {
            return null;
        }

        try {
            T result = serializer.fromJson(responseBody, responseType);

            if (result == null) {
                throw new ServerFacadeException(
                        "The chess server returned an empty response.");
            }

            return result;
        } catch (JsonParseException exception) {
            throw new ServerFacadeException(
                    "The chess server returned an unreadable response.",
                    exception);
        }
    }

    private static URI createServerUri(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("Server URL cannot be blank.");
        }

        URI uri = URI.create(serverUrl.trim());
        String scheme = uri.getScheme();
        boolean usesHttp = "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme);

        if (!usesHttp || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "Server URL must contain an HTTP scheme and host.");
        }

        return uri;
    }

    private record ErrorResponse(String message) {
    }
}
