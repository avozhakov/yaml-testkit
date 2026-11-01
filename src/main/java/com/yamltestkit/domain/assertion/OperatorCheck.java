package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.assertion.JsonPaths.PathResult;
import com.yamltestkit.domain.model.AssertOperator;
import com.yamltestkit.domain.model.BodyMode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * One {@code assert} operator applied to the value a JSONPath selected. Presence operators follow the spec on a
 * definite path: {@code exists} = key present, {@code absent} = key missing, {@code isNull} = missing or
 * {@code null}, {@code notNull} = present and not {@code null}. On an indefinite path (wildcard, filter) the matches
 * are the elements that have the key: {@code exists} = at least one match, {@code absent} = none, {@code isNull} /
 * {@code notNull} need at least one match and hold for every match. An indefinite path whose definite prefix is
 * missing fails every operator, so a misspelled key never passes vacuously.
 */
final class OperatorCheck {

    private OperatorCheck() {
    }

    /**
     * @return {@code null} when the operator holds, otherwise why it doesn't
     */
    static @Nullable String check(AssertOperator operator, JsonNode expected, PathResult actual,
                                  @Nullable BodyMode mode) {
        if (!actual.found() && !actual.definite()) {
            return actual.missingReason();
        }
        return switch (operator) {
            case EXISTS -> exists(actual);
            case ABSENT -> absent(actual);
            case IS_NULL -> isNull(actual);
            case NOT_NULL -> notNull(actual);
            default -> actual.found() ? checkValue(operator, expected, actual.value(), mode) : actual.missingReason();
        };
    }

    private static @Nullable String checkValue(AssertOperator operator, JsonNode expected, JsonNode actual,
                                               @Nullable BodyMode mode) {
        return switch (operator) {
            case EQUALS -> equalsCheck(expected, actual, mode);
            case NOT_EQUALS -> JsonComparator.difference(expected, actual, mode(mode)).isEmpty()
                    ? "expected a value other than " + abbreviate(expected) : null;
            case SIZE -> size(expected, actual);
            case GTE -> compare(expected, actual, true);
            case LTE -> compare(expected, actual, false);
            case CONTAINS -> contains(expected, actual);
            case CONTAINS_EXACTLY -> containsExactly(expected, actual);
            case CONTAINS_IN_ANY_ORDER -> containsInAnyOrder(expected, actual);
            case UNIQUE -> unique(actual);
            case STARTS_WITH -> startsWith(expected, actual);
            case MATCHES -> matches(expected, actual);
            case EXISTS, ABSENT, IS_NULL, NOT_NULL -> throw new IllegalStateException("presence operator " + operator);
        };
    }

    private static boolean present(PathResult actual) {
        return actual.definite() ? actual.found() : !actual.value().isEmpty();
    }

    private static @Nullable String exists(PathResult actual) {
        if (present(actual)) {
            return null;
        }
        return actual.definite() ? "expected the key to exist, but it is missing"
                : "expected at least one match, but there is none";
    }

    private static @Nullable String isNull(PathResult actual) {
        if (actual.definite()) {
            return !actual.found() || actual.value().isNull() ? null
                    : "expected null or a missing key, but was " + abbreviate(actual.value());
        }
        if (!present(actual)) {
            return "expected at least one match, all null, but there is none";
        }
        return elements(actual.value()).stream().allMatch(JsonNode::isNull) ? null
                : "expected every match to be null, but was " + abbreviate(actual.value());
    }

    private static @Nullable String notNull(PathResult actual) {
        if (!present(actual)) {
            return actual.definite() ? "expected a present non-null value, but the key is missing"
                    : "expected at least one match, none null, but there is none";
        }
        if (actual.definite()) {
            return actual.value().isNull() ? "expected a present non-null value, but it is null" : null;
        }
        return elements(actual.value()).stream().noneMatch(JsonNode::isNull) ? null
                : "expected every match to be non-null, but was " + abbreviate(actual.value());
    }

    private static @Nullable String absent(PathResult actual) {
        if (!present(actual)) {
            return null;
        }
        return actual.definite() && actual.value().isNull()
                ? "expected the key to be absent, but it is present with value null"
                : "expected the key to be absent, but it is present with value " + abbreviate(actual.value());
    }

    private static @Nullable String equalsCheck(JsonNode expected, JsonNode actual, @Nullable BodyMode mode) {
        return JsonComparator.difference(expected, actual, mode(mode))
                .map(difference -> difference.startsWith("expected ") ? difference
                        : difference + "; actual: " + abbreviate(actual))
                .orElse(null);
    }

    private static @Nullable String size(JsonNode expected, JsonNode actual) {
        int size;
        if (actual.isArray() || actual.isObject()) {
            size = actual.size();
        } else if (actual.isTextual()) {
            size = actual.asText().length();
        } else {
            return "expected an array, object or string but was " + abbreviate(actual);
        }
        return size == expected.asInt() ? null : "expected size " + expected.asInt() + " but was " + size
                + ": " + abbreviate(actual);
    }

    private static @Nullable String compare(JsonNode expected, JsonNode actual, boolean greaterOrEqual) {
        if (!actual.isNumber()) {
            return "expected a number but was " + abbreviate(actual);
        }
        int comparison = actual.decimalValue().compareTo(expected.decimalValue());
        boolean holds = greaterOrEqual ? comparison >= 0 : comparison <= 0;
        return holds ? null : "expected a number " + (greaterOrEqual ? ">= " : "<= ") + expected + " but was " + actual;
    }

    private static @Nullable String contains(JsonNode expected, JsonNode actual) {
        if (actual.isTextual()) {
            return containsText(expected, actual);
        }
        if (!actual.isArray()) {
            return "expected an array or a string but was " + abbreviate(actual);
        }
        List<JsonNode> items = expected.isArray() ? elements(expected) : List.of(expected);
        List<JsonNode> missing = items.stream()
                .filter(item -> elements(actual).stream().noneMatch(element -> JsonEquality.equal(item, element)))
                .toList();
        return missing.isEmpty() ? null : "expected an array containing " + missing + " but was " + abbreviate(actual);
    }

    /** A string contains a substring, or every substring of a list. */
    private static @Nullable String containsText(JsonNode expected, JsonNode actual) {
        List<JsonNode> substrings = expected.isArray() ? elements(expected) : List.of(expected);
        List<JsonNode> notText = substrings.stream().filter(item -> !item.isTextual()).toList();
        if (!notText.isEmpty()) {
            return "the actual value is a string, so `contains` needs a string or a list of strings, but got "
                    + abbreviate(expected);
        }
        List<JsonNode> missing = substrings.stream()
                .filter(item -> !actual.asText().contains(item.asText()))
                .toList();
        return missing.isEmpty() ? null
                : "expected a string containing " + (expected.isArray() ? abbreviate(missing.toString())
                : abbreviate(expected)) + " but was " + abbreviate(actual);
    }

    private static @Nullable String containsExactly(JsonNode expected, JsonNode actual) {
        if (!actual.isArray()) {
            return "expected an array but was " + abbreviate(actual);
        }
        boolean equal = expected.size() == actual.size();
        for (int i = 0; equal && i < expected.size(); i++) {
            equal = JsonEquality.equal(expected.get(i), actual.get(i));
        }
        return equal ? null : "expected exactly " + abbreviate(expected) + " in this order but was " + abbreviate(actual);
    }

    private static @Nullable String containsInAnyOrder(JsonNode expected, JsonNode actual) {
        if (!actual.isArray()) {
            return "expected an array but was " + abbreviate(actual);
        }
        List<JsonNode> remaining = new ArrayList<>(elements(actual));
        List<JsonNode> missing = new ArrayList<>();
        for (JsonNode item : expected) {
            int index = indexOf(remaining, item);
            if (index < 0) {
                missing.add(item);
            } else {
                remaining.remove(index);
            }
        }
        return missing.isEmpty() && remaining.isEmpty() ? null : "expected " + abbreviate(expected)
                + " in any order but was " + abbreviate(actual) + " (missing " + missing + ", unexpected " + remaining + ")";
    }

    private static @Nullable String unique(JsonNode actual) {
        if (!actual.isArray()) {
            return "expected an array but was " + abbreviate(actual);
        }
        List<JsonNode> seen = new ArrayList<>();
        List<JsonNode> duplicates = new ArrayList<>();
        for (JsonNode element : actual) {
            (indexOf(seen, element) < 0 ? seen : duplicates).add(element);
        }
        return duplicates.isEmpty() ? null : "expected unique values but found duplicates " + duplicates + " in "
                + abbreviate(actual);
    }

    private static @Nullable String startsWith(JsonNode expected, JsonNode actual) {
        if (!actual.isTextual()) {
            return "expected a string but was " + abbreviate(actual);
        }
        return actual.asText().startsWith(expected.asText()) ? null
                : "expected a string starting with " + abbreviate(expected) + " but was " + abbreviate(actual);
    }

    private static @Nullable String matches(JsonNode expected, JsonNode actual) {
        if (!actual.isTextual()) {
            return "expected a string but was " + abbreviate(actual);
        }
        return Pattern.compile(expected.asText()).matcher(actual.asText()).matches() ? null
                : "expected a string matching /" + expected.asText() + "/ but was " + abbreviate(actual);
    }

    private static int indexOf(List<JsonNode> nodes, JsonNode item) {
        for (int i = 0; i < nodes.size(); i++) {
            if (JsonEquality.equal(item, nodes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<JsonNode> elements(JsonNode array) {
        List<JsonNode> elements = new ArrayList<>(array.size());
        array.forEach(elements::add);
        return elements;
    }

    private static BodyMode mode(@Nullable BodyMode mode) {
        return mode == null ? BodyMode.LENIENT : mode;
    }
}
