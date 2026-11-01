package com.yamltestkit.domain.json;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shortens values quoted in failure messages, so a large body or document doesn't bury the message.
 */
public final class Abbreviations {

    static final int MAX_LENGTH = 300;

    private Abbreviations() {
    }

    public static String abbreviate(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH) + "…";
    }

    /** The value as compact JSON, {@code <missing>} for a missing node, shortened. */
    public static String abbreviate(JsonNode value) {
        return abbreviate(value.isMissingNode() ? "<missing>" : value.toString());
    }
}
