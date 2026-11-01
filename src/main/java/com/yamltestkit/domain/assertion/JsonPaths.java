package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.yamltestkit.domain.json.JsonMappers;

import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jayway JsonPath over Jackson trees, configured so a missing key and a {@code null} value stay distinguishable:
 * no {@code SUPPRESS_EXCEPTIONS}, no {@code DEFAULT_PATH_LEAF_TO_NULL}. A definite path yields one value or
 * "not found"; an indefinite path (wildcard, filter, deep scan, slice) yields the array of its matches, or "not
 * found" when its definite prefix (the part before the first wildcard / filter) is missing, so a misspelled key
 * never reads as "no matches".
 */
public final class JsonPaths {

    private static final String ROOT_PATH = "$";
    private static final Configuration READ = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider(JsonMappers.plain()))
            .mappingProvider(new JacksonMappingProvider(JsonMappers.plain()))
            .options(EnumSet.noneOf(Option.class))
            .build();
    private static final Configuration DELETE = READ.addOptions(Option.SUPPRESS_EXCEPTIONS);
    /** A definite segment of a normalized path: {@code ['name']} or {@code [3]} / {@code [-1]}. */
    private static final Pattern DEFINITE_SEGMENT = Pattern.compile("\\['(?:[^']|\\\\')*?']|\\[-?\\d+]");

    private JsonPaths() {
    }

    /**
     * @throws IllegalArgumentException when the path is not valid JSONPath
     */
    public static PathResult read(JsonNode root, String path) {
        JsonPath compiled = compile(path);
        if (!compiled.isDefinite()) {
            String prefix = definitePrefix(compiled);
            if (!ROOT_PATH.equals(prefix) && !exists(root, prefix)) {
                return PathResult.missingPrefix(prefix);
            }
        }
        try {
            Object value = compiled.read(root, READ);
            JsonNode node = switch (value) {
                case null -> NullNode.getInstance();
                case JsonNode tree -> tree;
                default -> JsonMappers.plain().valueToTree(value);
            };
            return PathResult.found(compiled.isDefinite(), node);
        } catch (PathNotFoundException e) {
            return compiled.isDefinite()
                    ? PathResult.MISSING_KEY
                    : PathResult.found(false, JsonMappers.plain().createArrayNode());
        }
    }

    /** A copy of {@code root} without the values at {@code paths}; paths that match nothing are ignored. */
    public static JsonNode delete(JsonNode root, List<String> paths) {
        JsonNode copy = root.deepCopy();
        paths.forEach(path -> compile(path).delete(copy, DELETE));
        return copy;
    }

    /** The normalized path up to its first segment that is not a plain key or index, e.g. {@code $['ads']}. */
    private static String definitePrefix(JsonPath compiled) {
        String normalized = compiled.getPath();
        Matcher segment = DEFINITE_SEGMENT.matcher(normalized);
        int end = ROOT_PATH.length();
        while (segment.find(end) && segment.start() == end) {
            end = segment.end();
        }
        return normalized.substring(0, end);
    }

    private static boolean exists(JsonNode root, String definitePath) {
        JsonPath prefix = JsonPath.compile(definitePath);
        if (!prefix.isDefinite()) {
            return true;
        }
        try {
            prefix.read(root, READ);
            return true;
        } catch (PathNotFoundException e) {
            return false;
        }
    }

    private static JsonPath compile(String path) {
        try {
            return JsonPath.compile(path);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("invalid JSONPath `" + path + "`: " + e.getMessage(), e);
        }
    }

    /**
     * @param found         false for a definite path whose key is missing, or an indefinite path whose definite
     *                      prefix is missing
     * @param definite      whether the path selects a single value
     * @param value         the value, the array of matches of an indefinite path, or a {@code MissingNode}
     * @param missingReason why nothing was found, empty when {@code found}
     */
    public record PathResult(boolean found, boolean definite, JsonNode value, String missingReason) {

        static final PathResult MISSING_KEY = new PathResult(false, true, MissingNode.getInstance(),
                "the key is missing");

        static PathResult found(boolean definite, JsonNode value) {
            return new PathResult(true, definite, value, "");
        }

        static PathResult missingPrefix(String prefix) {
            return new PathResult(false, false, MissingNode.getInstance(), "path prefix `" + prefix + "` missing");
        }

        /** The same path result with another value, e.g. the parsed form of a JSON string. */
        PathResult withValue(JsonNode newValue) {
            return new PathResult(found, definite, newValue, missingReason);
        }
    }
}
