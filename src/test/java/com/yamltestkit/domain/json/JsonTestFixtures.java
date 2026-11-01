package com.yamltestkit.domain.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared test data factories for the testkit's own tests.
 */
public final class JsonTestFixtures {

    private JsonTestFixtures() {
    }

    /** The JSON text as a Jackson tree; invalid JSON is a bug in the test. */
    public static JsonNode json(String text) {
        try {
            return JsonMappers.plain().readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid JSON in a test: " + text, e);
        }
    }
}
