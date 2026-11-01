package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;

/**
 * Deep JSON equality where numbers compare by value ({@code 4}, {@code 4L} and {@code 4.0} are equal), since YAML,
 * Jackson and protobuf choose different numeric node types for the same number.
 */
public final class JsonEquality {

    private static final Comparator<JsonNode> NUMERIC_AWARE = (left, right) -> {
        if (left.equals(right)) {
            return 0;
        }
        boolean numericallyEqual = left.isNumber() && right.isNumber()
                && left.decimalValue().compareTo(right.decimalValue()) == 0;
        return numericallyEqual ? 0 : 1;
    };

    private JsonEquality() {
    }

    public static boolean equal(JsonNode left, JsonNode right) {
        return left.equals(NUMERIC_AWARE, right);
    }
}
