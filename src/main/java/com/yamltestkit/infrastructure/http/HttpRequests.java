package com.yamltestkit.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.Query;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the request of a {@code call.http} to the application on {@code localhost}: {@code query} parameters are
 * encoded (a space as {@code %20}), {@code rawQuery} is sent as written. A body that is not a YAML string is sent as
 * JSON with {@code Content-Type: application/json} unless the case sets that header.
 */
public final class HttpRequests {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON = "application/json";

    private HttpRequests() {
    }

    public static HttpRequest request(Call.Http call, int port, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(call, port))
                .timeout(timeout)
                .method(call.method(), body(call.body()));
        call.headers().forEach(builder::header);
        if (needsJsonContentType(call)) {
            builder.header(CONTENT_TYPE, JSON);
        }
        return builder.build();
    }

    static URI uri(Call.Http call, int port) {
        String query = switch (call.query()) {
            case Query.Raw raw -> raw.value();
            case Query.Params params -> encode(params.values());
        };
        return URI.create("http://localhost:" + port + call.path() + (query.isEmpty() ? "" : "?" + query));
    }

    private static String encode(Map<String, List<String>> params) {
        return params.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(value -> encode(entry.getKey()) + "=" + encode(value)))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static HttpRequest.BodyPublisher body(JsonNode body) {
        if (body.isMissingNode()) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(body.isTextual() ? body.asText() : body.toString());
    }

    private static boolean needsJsonContentType(Call.Http call) {
        return !call.body().isMissingNode() && !call.body().isTextual()
                && call.headers().keySet().stream().noneMatch(CONTENT_TYPE::equalsIgnoreCase);
    }
}
