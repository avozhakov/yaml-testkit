package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A WireMock response; {@code body} is a {@code MissingNode} when not given.
 *
 * @param fault    WireMock fault name, {@code null} for a normal response
 * @param template render the response with WireMock response templating
 */
public record StubResponse(
        int status,
        Map<String, String> headers,
        JsonNode body,
        int delayMs,
        @Nullable String fault,
        boolean template
) {

    public static final int DEFAULT_STATUS = 200;

    public StubResponse {
        headers = Map.copyOf(headers);
    }
}
