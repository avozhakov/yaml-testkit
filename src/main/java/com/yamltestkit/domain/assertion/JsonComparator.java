package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.model.BodyMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * Whole-value JSON comparison of {@code body}, {@code returns} and {@code equals} / {@code notEquals}, walking both
 * Jackson trees: {@link BodyMode#LENIENT} allows extra actual fields, {@link BodyMode#STRICT} forbids them; array
 * order and length are significant in both. Leaves compare by {@link JsonEquality}, so numbers compare exactly as
 * {@code BigDecimal} — two 19-digit ids that differ in the last digit differ. Raw-text {@link BodyMode#EXACT}
 * compares are {@link BodyAssertion}'s; on trees it behaves like {@code STRICT}.
 */
public final class JsonComparator {

    private static final String ROOT = "$";

    private JsonComparator() {
    }

    /**
     * @return the differences, each naming its JSONPath; empty when the values match
     */
    public static Optional<String> difference(JsonNode expected, JsonNode actual, BodyMode mode) {
        List<String> differences = new ArrayList<>();
        compare(ROOT, expected, actual, mode != BodyMode.LENIENT, differences);
        return differences.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", differences));
    }

    private static void compare(String path, JsonNode expected, JsonNode actual, boolean strict,
                                List<String> differences) {
        if (expected.isObject() && actual.isObject()) {
            compareObjects(path, expected, actual, strict, differences);
        } else if (expected.isArray() && actual.isArray()) {
            compareArrays(path, expected, actual, strict, differences);
        } else if (!JsonEquality.equal(expected, actual)) {
            differences.add(at(path) + "expected " + abbreviate(expected) + " but was " + abbreviate(actual));
        }
    }

    private static void compareObjects(String path, JsonNode expected, JsonNode actual, boolean strict,
                                       List<String> differences) {
        for (Map.Entry<String, JsonNode> field : expected.properties()) {
            JsonNode actualValue = actual.get(field.getKey());
            String fieldPath = path + "." + field.getKey();
            if (actualValue == null) {
                differences.add(fieldPath + ": missing, expected " + abbreviate(field.getValue()));
            } else {
                compare(fieldPath, field.getValue(), actualValue, strict, differences);
            }
        }
        if (strict) {
            actual.properties().stream()
                    .filter(field -> !expected.has(field.getKey()))
                    .forEach(field -> differences.add(path + "." + field.getKey() + ": unexpected field with value "
                            + abbreviate(field.getValue())));
        }
    }

    private static void compareArrays(String path, JsonNode expected, JsonNode actual, boolean strict,
                                      List<String> differences) {
        if (expected.size() != actual.size()) {
            differences.add(at(path) + "expected " + expected.size() + " elements but was " + actual.size() + ": "
                    + abbreviate(actual));
            return;
        }
        for (int i = 0; i < expected.size(); i++) {
            compare(path + "[" + i + "]", expected.get(i), actual.get(i), strict, differences);
        }
    }

    /** The path prefix of a difference; none at the root, so a scalar mismatch reads "expected 5 but was 4". */
    private static String at(String path) {
        return ROOT.equals(path) ? "" : path + ": ";
    }
}
