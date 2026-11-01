package com.yamltestkit.domain.model;

/**
 * Expected number of calls of a verification. {@code never: true} is {@code Times(0)}; a check without a count
 * expects at least one call.
 */
public sealed interface CallCount permits CallCount.Times, CallCount.AtLeast, CallCount.NoInteractions {

    CallCount AT_LEAST_ONCE = new AtLeast(1);

    record Times(int times) implements CallCount {
    }

    record AtLeast(int times) implements CallCount {
    }

    /** {@code noInteractions: true}: no call of any method of the mock. */
    record NoInteractions() implements CallCount {
    }
}
