package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yamltestkit.domain.model.CaseDefinitionException;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads values out of schema-validated YAML trees. Absent keys give {@code null} / empty collections, so binders
 * map them onto the model's "not given" representation.
 */
final class Nodes {

    private static final String DEFINE = "define";

    private static final Pattern DURATION = Pattern.compile("^(\\d+)(ms|s|m)$");

    private Nodes() {
    }

    /**
     * Removes the top-level {@code define} block of a schema-validated file. Its anchors are already resolved into
     * the aliases that use them, so the block itself is never bound or applied.
     */
    static void dropDefine(JsonNode validatedFile) {
        ((ObjectNode) validatedFile).remove(DEFINE);
    }

    static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static String requiredText(JsonNode node, String field, String where) {
        String value = text(node, field);
        if (value == null) {
            throw new CaseDefinitionException(where + " has no `" + field + "`");
        }
        return value;
    }

    static @Nullable Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    static boolean flag(JsonNode node, String field) {
        return node.path(field).asBoolean(false);
    }

    static @Nullable List<String> stringListOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? null : list(value, JsonNode::asText);
    }

    static List<String> stringList(JsonNode node, String field) {
        List<String> values = stringListOrNull(node, field);
        return values == null ? List.of() : values;
    }

    static <T> List<T> list(JsonNode array, Function<JsonNode, T> mapper) {
        List<T> values = new ArrayList<>(array.size());
        array.forEach(item -> values.add(mapper.apply(item)));
        return Collections.unmodifiableList(values);
    }

    static <T> List<T> listField(JsonNode node, String field, Function<JsonNode, T> mapper) {
        JsonNode value = node.get(field);
        return value == null ? List.of() : list(value, mapper);
    }

    /** An object of scalars as an insertion-ordered map of their text values. */
    static Map<String, String> scalarMap(JsonNode node, String field) {
        Map<String, String> values = new LinkedHashMap<>();
        node.path(field).properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return Collections.unmodifiableMap(values);
    }

    static @Nullable Duration duration(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        Matcher matcher = DURATION.matcher(value);
        if (!matcher.matches()) {
            throw new CaseDefinitionException("invalid duration `" + value + "` (expected e.g. 200ms, 3s, 1m)");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            default -> Duration.ofMinutes(amount);
        };
    }
}
