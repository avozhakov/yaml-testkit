package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * {@code expect.body}. For {@link BodyMode#EXACT} {@code expected} is a text node holding the raw expected text.
 *
 * @param ignore JSONPaths removed from both sides before comparing
 */
public record BodyExpectation(JsonNode expected, BodyMode mode, List<String> ignore) {

    public BodyExpectation {
        ignore = List.copyOf(ignore);
    }
}
