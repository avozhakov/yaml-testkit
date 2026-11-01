package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Request matching of a WireMock stub or a service verification. Absent parts match anything; {@code body} is a
 * {@code MissingNode} when not given. Header names are in lowercase, since HTTP header names are case-insensitive.
 */
public record RequestMatcher(
        @Nullable String method,
        @Nullable String path,
        @Nullable String pathPattern,
        Map<String, String> query,
        Map<String, String> headers,
        JsonNode body
) {

    public RequestMatcher {
        query = Map.copyOf(query);
        headers = Map.copyOf(headers);
    }
}
