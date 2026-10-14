package server.handler;

import com.google.gson.Gson;
import io.javalin.http.Context;

/**
 * Writes application responses with a consistent status, content type, and JSON body.
 */
final class JsonResponseWriter {

    private JsonResponseWriter() {
    }

    public static void write(
            Context context,
            int statusCode,
            Object responseBody,
            Gson serializer) {
        context.status(statusCode);
        context.contentType("application/json");
        context.result(serializer.toJson(responseBody));
    }
}
