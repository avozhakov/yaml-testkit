package com.yamltestkit.infrastructure.logback;

import com.yamltestkit.domain.model.LogExpectation;
import org.opentest4j.AssertionFailedError;

import java.util.Collection;
import java.util.List;

/**
 * Stands in for log capture when Logback is not the SLF4J backend: nothing is captured, and any {@code expect.logs}
 * check fails naming the active backend.
 *
 * @param backend class name of the active SLF4J logger factory
 */
record UnavailableLogCapture(String backend) implements LogCapture {

    @Override
    public void attach(Collection<String> loggers) {
        // Nothing to attach to: check() reports the missing backend for each case that expects log events.
    }

    @Override
    public void clear() {
        // Nothing is captured.
    }

    @Override
    public void close() {
        // Nothing was attached.
    }

    @Override
    public List<AssertionFailedError> check(List<LogExpectation> expectations, String where) {
        if (expectations.isEmpty()) {
            return List.of();
        }
        return List.of(new AssertionFailedError(where + ": expect.logs needs Logback (Spring Boot's default logging"
                + " backend), but the active SLF4J backend is " + backend));
    }
}
