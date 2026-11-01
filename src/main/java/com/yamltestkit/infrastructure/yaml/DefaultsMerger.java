package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * Merges a file's {@code defaults} into one of its cases. Structural objects ({@code call}, its {@code query},
 * {@code headers} and {@code deps}, {@code expect}, {@code bodyText}) merge key by key; any other value
 * of the case, payloads included, replaces the default (case values win, a YAML {@code null} included).
 * {@code mocks}, {@code spies} and {@code http} are not merged: they form their own stub layer, so a case's stub
 * never drops a default stub on another method.
 */
final class DefaultsMerger {

    static final Set<String> STUB_KEYS = Set.of("mocks", "spies", "http");

    private static final Set<String> MERGED_OBJECTS = Set.of(
            "call", "call.query", "call.headers", "call.deps",
            "expect", "expect.headers", "expect.bodyText");

    private DefaultsMerger() {
    }

    static ObjectNode merge(JsonNode defaults, ObjectNode caseNode) {
        ObjectNode merged = caseNode.objectNode();
        defaults.properties().stream()
                .filter(entry -> !STUB_KEYS.contains(entry.getKey()))
                .forEach(entry -> merged.set(entry.getKey(), entry.getValue().deepCopy()));
        mergeInto(merged, caseNode, "");
        return merged;
    }

    private static void mergeInto(ObjectNode target, JsonNode overrides, String prefix) {
        overrides.properties().forEach(entry -> {
            String path = prefix + entry.getKey();
            JsonNode current = target.get(entry.getKey());
            JsonNode value = entry.getValue();
            if (MERGED_OBJECTS.contains(path) && current != null && current.isObject() && value.isObject()) {
                mergeInto((ObjectNode) current, value, path + ".");
            } else {
                target.set(entry.getKey(), value.deepCopy());
            }
        });
    }
}
