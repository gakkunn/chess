package server;

import dataaccess.MemoryGameDAO;
import dataaccess.MemoryUserDAO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.result.ErrorResult;
import testutil.FailingAuthDAO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;

public class ClearEndpointTests extends EndpointTestSupport {

    @Test
    public void deleteDatabaseReturnsEmptySuccessResponse() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server()).resolve("db");

        HttpResponse<String> response = sendRequest(endpoint, "DELETE", null, null);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("{}", response.body());
        Assertions.assertTrue(response.headers()
                .firstValue("Content-Type")
                .orElse("")
                .startsWith("application/json"));
    }

    @Test
    public void deleteDatabaseCanRunMoreThanOnce() throws IOException, InterruptedException {
        URI endpoint = startServer(new Server()).resolve("db");

        HttpResponse<String> firstResponse = sendRequest(endpoint, "DELETE", null, null);
        HttpResponse<String> secondResponse = sendRequest(endpoint, "DELETE", null, null);

        Assertions.assertEquals(200, firstResponse.statusCode());
        Assertions.assertEquals(200, secondResponse.statusCode());
        Assertions.assertEquals("{}", secondResponse.body());
    }

    @Test
    public void dataAccessFailureReturnsJsonInternalServerError() throws IOException, InterruptedException {
        Server server = new Server(
                new MemoryUserDAO(),
                new FailingAuthDAO(),
                new MemoryGameDAO());
        URI endpoint = startServer(server).resolve("db");

        HttpResponse<String> response = sendRequest(endpoint, "DELETE", null, null);
        ErrorResult errorResult = serializer.fromJson(response.body(), ErrorResult.class);

        Assertions.assertEquals(500, response.statusCode());
        Assertions.assertNotNull(errorResult);
        Assertions.assertTrue(errorResult.message().startsWith("Error:"));
        Assertions.assertTrue(errorResult.message().contains("Authentication storage is unavailable"));
    }

}
