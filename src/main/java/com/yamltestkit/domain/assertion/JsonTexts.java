package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yamltestkit.domain.json.JsonMappers;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * {@code parseJson}: JSON held in string values, as in a logged response string or a diagnostic payload field.
 */
public final class JsonTexts {

    private JsonTexts() {
    }

    /**
     * A JSON string parsed, or each JSON string of an array (the matches of an indefinite path) parsed.
     *
     * @throws IllegalArgumentException when the value is not a string (or array of strings) holding valid JSON
     */
    public static JsonNode parse(JsonNode value) {
        if (value.isTextual()) {
            return parseText(value.asText());
        }
        if (value.isArray()) {
            ArrayNode parsed = JsonMappers.plain().createArrayNode();
            value.forEach(element -> parsed.add(parse(element)));
            return parsed;
        }
        throw new IllegalArgumentException("expected a JSON string but was " + abbreviate(value));
    }

    private static JsonNode parseText(String text) {
        try {
            return JsonMappers.plain().readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("not valid JSON: " + abbreviate(text) + " (" + e.getOriginalMessage()
                    + ")", e);
        }
    }
}
