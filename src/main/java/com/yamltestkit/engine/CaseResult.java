package com.yamltestkit.engine;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of running one case: passed, or failed with the throwable that describes why.
 */
public record CaseResult(String casePath, @Nullable Throwable failure) {

    public static CaseResult passed(String casePath) {
        return new CaseResult(casePath, null);
    }

    public static CaseResult failed(String casePath, Throwable failure) {
        return new CaseResult(casePath, failure);
    }

    public boolean isPassed() {
        return failure == null;
    }
}
