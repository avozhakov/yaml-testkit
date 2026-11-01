package com.yamltestkit.infrastructure.logback;

import com.yamltestkit.domain.model.LogExpectation;
import org.opentest4j.AssertionFailedError;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * Captures the log events {@code expect.logs} checks (design D11): one buffer for the run, fed by an appender on each
 * logger named by an {@code expect.logs} entry, cleared at the start of each case and after a shared context starts.
 * Only Logback (Spring Boot's default) is supported; with another SLF4J backend, every {@code expect.logs} check
 * fails saying so, and cases without {@code expect.logs} are not affected. Logback classes are referenced only by the
 * Logback implementation, which is loaded only when Logback is the active backend.
 */
public interface LogCapture extends AutoCloseable {

    /** Class of the SLF4J logger factory when Logback is the backend. */
    String LOGBACK_FACTORY = "ch.qos.logback.classic.LoggerContext";

    static LogCapture create() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        return LOGBACK_FACTORY.equals(factory.getClass().getName())
                ? LogbackCapture.of(factory)
                : new UnavailableLogCapture(factory.getClass().getName());
    }

    /**
     * Adds loggers to capture and (re)attaches the appender to every logger named so far; an event is captured once
     * even when both a logger and its ancestor are named. Idempotent. Called on every context start and before every
     * case, since starting a context may reset the logging configuration and drop the appender.
     */
    void attach(Collection<String> loggers);

    /** Forgets the captured events. */
    void clear();

    /**
     * @param where the step, prefixed to failure messages
     * @return the failed expectations, empty when all hold
     */
    List<AssertionFailedError> check(List<LogExpectation> expectations, String where);

    /**
     * Detaches the appender and forgets the events, so tests that run after the engine in the same JVM don't keep
     * feeding it.
     */
    @Override
    void close();
}
