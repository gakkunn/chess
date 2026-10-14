package server.handler;

import com.google.gson.Gson;
import dataaccess.DataAccessException;
import io.javalin.http.Context;
import service.ClearService;
import service.request.ClearRequest;
import service.result.ClearResult;

/**
 * Translates the clear HTTP request into a service operation and JSON response.
 */
public class ClearHandler {

    private final ClearService clearService;
    private final Gson serializer;

    public ClearHandler(ClearService clearService, Gson serializer) {
        this.clearService = clearService;
        this.serializer = serializer;
    }

    public void handleClear(Context context) throws DataAccessException {
        ClearResult result = clearService.clear(new ClearRequest());

        JsonResponseWriter.write(context, 200, result, serializer);
    }
}
