package com.yamltestkit.selftest.app.tracking;

import com.yamltestkit.selftest.app.ApiException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Posts ad items to the tracking service and returns their click URLs by item id (imitates the tracking-URL
 * generator client): like a generated client it throws {@link ApiException} with the status for a non-2xx answer and
 * with the cause for an I/O error.
 */
@Component
public class TrackingClient {

    private final TrackingProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient client;
    private final URI urlsEndpoint;

    public TrackingClient(TrackingProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        // HTTP/1.1, like the generated clients it imitates.
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        this.urlsEndpoint = URI.create("http://" + properties.host() + ":" + properties.port() + "/urls");
    }

    public Map<String, String> clickUrls(List<TrackingItem> items) throws ApiException {
        HttpRequest request = HttpRequest.newBuilder(urlsEndpoint)
                .timeout(properties.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(new TrackingRequest(items))))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() / 100 != 2) {
            throw new ApiException(response.statusCode(), response.body());
        }
        return jsonMapper.readValue(response.body(), TrackingResponse.class).urls().stream()
                .collect(Collectors.toMap(TrackingUrl::id, TrackingUrl::clickUrl, (first, second) -> first));
    }

    private HttpResponse<String> send(HttpRequest request) throws ApiException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ApiException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(e);
        }
    }
}
