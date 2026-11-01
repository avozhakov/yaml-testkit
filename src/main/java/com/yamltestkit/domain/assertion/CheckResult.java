package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;

import java.util.List;

/**
 * Outcome of one {@code verify} check.
 *
 * @param failures the count, argument and nested {@code assert} failures; empty when the check holds
 * @param captured the matching calls as JSON, built only when the check asserts on them or keeps them ({@code as});
 *                 {@code null} otherwise or when they can't be converted (a failure says so)
 */
public record CheckResult(List<AssertionFailedError> failures, @Nullable JsonNode captured) {

    public CheckResult {
        failures = List.copyOf(failures);
    }

    public boolean passed() {
        return failures.isEmpty();
    }
}
