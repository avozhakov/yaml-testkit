package com.yamltestkit.domain.assertion;

import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.util.List;

/**
 * Reports the failed expectations of a step: one failure as itself, several together.
 */
public final class Failures {

    private Failures() {
    }

    public static void throwIfAny(List<AssertionFailedError> failures, String where) {
        if (failures.size() == 1) {
            throw failures.getFirst();
        }
        if (!failures.isEmpty()) {
            throw new MultipleFailuresError(where + ": " + failures.size() + " expectations failed", failures);
        }
    }
}
