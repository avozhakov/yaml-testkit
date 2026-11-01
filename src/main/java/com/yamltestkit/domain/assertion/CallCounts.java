package com.yamltestkit.domain.assertion;

import com.yamltestkit.domain.model.CallCount;

import java.util.Optional;

/** Compares an actual number of calls with the expected {@link CallCount} of a check. */
public final class CallCounts {

    private CallCounts() {
    }

    /** @return what was expected and what happened, empty when the count holds */
    public static Optional<String> mismatch(CallCount expected, int actual) {
        boolean holds = switch (expected) {
            case CallCount.Times times -> actual == times.times();
            case CallCount.AtLeast atLeast -> actual >= atLeast.times();
            case CallCount.NoInteractions ignored -> actual == 0;
        };
        return holds ? Optional.empty() : Optional.of("expected " + describe(expected) + ", actual: " + actual);
    }

    private static String describe(CallCount count) {
        return switch (count) {
            case CallCount.Times times when times.times() == 0 -> "none (never)";
            case CallCount.Times times -> "times: " + times.times();
            case CallCount.AtLeast atLeast -> "atLeast: " + atLeast.times();
            case CallCount.NoInteractions ignored -> "none (noInteractions)";
        };
    }
}
