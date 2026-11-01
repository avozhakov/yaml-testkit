package com.yamltestkit.selftest.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Calls the partner service at {@code partner.base-url}, which the cases point at WireMock (imitates the Amazon
 * transport): {@link #search} retries a 5xx status, an I/O error or a timeout {@code partner.retries} times.
 */
@Slf4j
@Component
public class PartnerClient {

    private static final int SERVER_ERROR = 500;

    private final PartnerProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient client;

    public PartnerClient(PartnerProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        // HTTP/1.1, like the generated clients it imitates.
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
    }

    public String item(String id) {
        HttpRequest request = HttpRequest.newBuilder(properties.baseUrl().resolve("/items/" + id))
                .timeout(properties.timeout())
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException e) {
            throw new UncheckedIOException("partner call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while calling the partner", e);
        }
    }

    public List<PartnerItem> search(String query, int limit) {
        HttpRequest request = HttpRequest.newBuilder(properties.baseUrl().resolve("/search"))
                .timeout(properties.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(new PartnerSearch(query, limit))))
                .build();
        int attempts = properties.retries() + 1;
        String failure = "";
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < SERVER_ERROR) {
                    return items(response);
                }
                failure = "status " + response.statusCode();
            } catch (IOException e) {
                failure = e.toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PartnerUnavailableException("interrupted while calling the partner", e);
            }
            log.warn("partner search attempt {} of {} failed: {}", attempt, attempts, failure);
        }
        throw new PartnerUnavailableException("partner search failed after " + attempts + " attempts: " + failure);
    }

    private List<PartnerItem> items(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            throw new PartnerUnavailableException("partner rejected the search with status " + response.statusCode());
        }
        return jsonMapper.readValue(response.body(), PartnerSearchResult.class).items();
    }
}
