package com.yamltestkit.selftest.unit;

/** Two constructor parameters of one type: a {@code deps} key naming that type matches both, so it is ambiguous. */
public record LimitRange(Limits lower, Limits upper) {

    public int width() {
        return upper.max() - lower.max();
    }
}
