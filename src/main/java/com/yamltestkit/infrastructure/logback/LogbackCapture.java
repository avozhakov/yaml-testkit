package com.yamltestkit.infrastructure.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.yamltestkit.domain.model.LogExpectation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;
import org.slf4j.ILoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link LogCapture} on Logback: one appender attached directly to every named logger, not to the root, so events are
 * captured whatever the configuration's additivity and root appenders, a non-additive named descendant included.
 * Logback hands one event instance to the appenders of the logger and of each additive ancestor, so when a logger and
 * its ancestor are both named the appender receives that instance twice; it keeps each instance once. All application
 * contexts share one {@link LoggerContext}; a context start may reset it, which detaches and stops the appender, so
 * {@link #attach} restarts and re-attaches it. Thread-safe: application code logs from any thread.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class LogbackCapture implements LogCapture {

    private static final String APPENDER_NAME = "yamlTestkit";

    private final LoggerContext context;
    private final DistinctEventAppender appender = new DistinctEventAppender();
    private final Set<String> loggers = new LinkedHashSet<>();

    static LogCapture of(ILoggerFactory factory) {
        return new LogbackCapture((LoggerContext) factory);
    }

    @Override
    public synchronized void attach(Collection<String> names) {
        loggers.addAll(names);
        if (loggers.isEmpty()) {
            return;
        }
        if (!appender.isStarted()) {
            appender.setContext(context);
            appender.setName(APPENDER_NAME);
            appender.start();
        }
        for (String name : loggers) {
            Logger logger = context.getLogger(name);
            if (!logger.isAttached(appender)) {
                logger.addAppender(appender);
            }
        }
    }

    @Override
    public void clear() {
        appender.clear();
    }

    @Override
    public List<AssertionFailedError> check(List<LogExpectation> expectations, String where) {
        List<LogEvent> events = events();
        List<AssertionFailedError> failures = new ArrayList<>();
        for (LogExpectation expectation : expectations) {
            LogMatcher.failure(expectation, events, filteredBy(expectation))
                    .ifPresent(failure -> failures.add(new AssertionFailedError(where + ": logs: " + failure)));
        }
        return failures;
    }

    @Override
    public synchronized void close() {
        loggers.forEach(name -> context.getLogger(name).detachAppender(appender));
        loggers.clear();
        appender.stop();
        clear();
    }

    /** The logger's effective level when it is above the expected level, so such events are never logged. */
    private @Nullable String filteredBy(LogExpectation expectation) {
        Level effective = context.getLogger(expectation.logger()).getEffectiveLevel();
        return Level.toLevel(expectation.level().name()).isGreaterOrEqual(effective) ? null : effective.toString();
    }

    private List<LogEvent> events() {
        return appender.events().stream()
                .map(event -> new LogEvent(event.getLoggerName(),
                        LogExpectation.LogLevel.valueOf(event.getLevel().toString()), event.getFormattedMessage()))
                .toList();
    }

    /**
     * Keeps each event instance once, in arrival order. Identity, not equality: two separate calls logging the same
     * message are two events. {@code AppenderBase.doAppend} synchronizes on the appender, so {@link #append} and the
     * methods below all run under its lock.
     */
    private static final class DistinctEventAppender extends AppenderBase<ILoggingEvent> {

        private final List<ILoggingEvent> events = new ArrayList<>();
        private final Set<ILoggingEvent> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        @Override
        protected void append(ILoggingEvent event) {
            if (seen.add(event)) {
                events.add(event);
            }
        }

        synchronized List<ILoggingEvent> events() {
            return List.copyOf(events);
        }

        synchronized void clear() {
            events.clear();
            seen.clear();
        }
    }
}
