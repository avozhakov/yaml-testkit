package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Reference {@code { ref, path?, parseJson? }} in an expected value: the result of an earlier step (response body
 * of {@code http}, return value JSON of {@code bean} / {@code class}) or a {@code verify[].as} capture.
 *
 * @param path JSONPath into the referenced value, {@code null} for the whole value
 */
public record ValueRef(String name, @Nullable String path, boolean parseJson) {

    private static final Set<String> KEYS = Set.of("ref", "path", "parseJson");

    /** True when the node has the shape of a reference: an object with a textual {@code ref} and no other keys. */
    public static boolean isRef(JsonNode node) {
        if (!node.isObject() || !node.path("ref").isTextual()) {
            return false;
        }
        for (var names = node.fieldNames(); names.hasNext(); ) {
            if (!KEYS.contains(names.next())) {
                return false;
            }
        }
        return true;
    }

    public static ValueRef of(JsonNode node) {
        if (!isRef(node)) {
            throw new IllegalArgumentException("not a reference: " + node);
        }
        JsonNode path = node.get("path");
        return new ValueRef(node.get("ref").asText(), path == null ? null : path.asText(),
                node.path("parseJson").asBoolean(false));
    }
}
