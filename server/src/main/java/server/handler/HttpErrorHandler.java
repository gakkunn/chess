package server.handler;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dataaccess.DataAccessException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import service.exception.AlreadyTakenException;
import service.exception.BadRequestException;
import service.exception.UnauthorizedException;
import service.result.ErrorResult;

/**
 * Converts application exceptions into consistent JSON HTTP responses.
 */
public final class HttpErrorHandler {

    private HttpErrorHandler() {
    }

    public static void register(Javalin javalin, Gson serializer) {
        javalin.exception(BadRequestException.class, (exception, context) ->
                writeError(context, 400, exception.getMessage(), serializer));

        javalin.exception(JsonParseException.class, (exception, context) ->
                writeError(context, 400, "Error: bad request", serializer));

        javalin.exception(UnauthorizedException.class, (exception, context) ->
                writeError(context, 401, exception.getMessage(), serializer));

        javalin.exception(AlreadyTakenException.class, (exception, context) ->
                writeError(context, 403, exception.getMessage(), serializer));

        javalin.exception(DataAccessException.class, (exception, context) ->
                writeError(context, 500, exception.getMessage(), serializer));

        javalin.exception(Exception.class, (exception, context) ->
                writeError(context, 500, "Error: internal server error", serializer));
    }

    private static void writeError(
            Context context,
            int statusCode,
            String message,
            Gson serializer) {
        ErrorResult result = new ErrorResult(formatErrorMessage(message));
        JsonResponseWriter.write(context, statusCode, result, serializer);
    }

    private static String formatErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Error: internal server error";
        }

        if (message.startsWith("Error:")) {
            return message;
        }

        return "Error: " + message;
    }
}
