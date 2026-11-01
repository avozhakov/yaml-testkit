package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * One {@code assert} entry: a JSONPath and its operators. Operator values are resolved from {@code { file }}; a
 * {@code { ref }} value stays as written (see {@link ValueRef}).
 *
 * @param mode object comparison mode for {@code equals}, {@code null} for the default (lenient)
 * @param all  nested assertion applied to every element of the matched array, {@code null} when absent
 * @param none nested assertion that no element of the matched array may satisfy, {@code null} when absent
 */
public record Assertion(
        String path,
        boolean parseJson,
        @Nullable BodyMode mode,
        Map<AssertOperator, JsonNode> operators,
        @Nullable Assertion all,
        @Nullable Assertion none
) {

    public Assertion {
        operators = Map.copyOf(operators);
    }
}
