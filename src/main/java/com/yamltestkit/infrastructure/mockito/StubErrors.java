package com.yamltestkit.infrastructure.mockito;

import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.List;

/**
 * Errors raised inside stubs while the code under test runs, e.g. an actual argument that can't be converted to JSON
 * for an {@code args} matcher. They are collected per case and reported after the call with their YAML location,
 * instead of being thrown into the code under test, which might catch them. Thread-safe: code under test may call
 * mocks from other threads.
 */
public final class StubErrors {

    private final List<AssertionFailedError> failures = new ArrayList<>();

    /** @param message names the YAML location of the stub */
    public synchronized void record(String message, Throwable cause) {
        failures.add(new AssertionFailedError(message, cause));
    }

    /** The errors recorded since the last call, which are then forgotten. */
    public synchronized List<AssertionFailedError> drain() {
        List<AssertionFailedError> drained = List.copyOf(failures);
        failures.clear();
        return drained;
    }
}
