package com.yamltestkit.infrastructure.logback;

import com.yamltestkit.domain.model.LogExpectation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * Checks one {@code expect.logs} entry against the captured events: an event matches when its logger is the named
 * logger or a descendant, its level is the expected level and its formatted message contains {@code contains}. Without
 * {@code times} at least one event must match; {@code absent} requires none. When the logging configuration filters
 * the expected level out for the named logger, a failing entry reports that and the {@code logging.level.<logger>}
 * property to set instead of a missing event; an entry that expects no event ({@code absent}, {@code times: 0})
 * fails too, since it would pass without checking anything.
 */
final class LogMatcher {

    private LogMatcher() {
    }

    /**
     * @param filteredBy the named logger's effective level when it is above the expected level, else {@code null}
     * @return why the entry fails, empty when it holds
     */
    static Optional<String> failure(LogExpectation expected, List<LogEvent> events, @Nullable String filteredBy) {
        long matching = events.stream().filter(event -> matches(expected, event)).count();
        boolean expectsNone = expected.absent() || Integer.valueOf(0).equals(expected.times());
        Optional<String> mismatch = mismatch(expected, matching, events);
        if (filteredBy != null && (expectsNone || mismatch.isPresent())) {
            return Optional.of(expected.level() + " events of " + expected.logger() + " are never logged: the logger's"
                    + " effective level is " + filteredBy + "; set `logging.level." + expected.logger() + ": "
                    + expected.level() + "` in the `properties` of _context.yaml, or lower the level in the logging"
                    + " configuration");
        }
        return mismatch;
    }

    private static Optional<String> mismatch(LogExpectation expected, long matching, List<LogEvent> events) {
        if (expected.absent()) {
            return matching == 0 ? Optional.empty() : Optional.of("expected no " + describe(expected)
                    + " (absent) but " + matching + " were logged; " + captured(expected, events));
        }
        if (expected.times() != null) {
            return matching == expected.times() ? Optional.empty() : Optional.of("expected times: "
                    + expected.times() + " of " + describe(expected) + ", actual: " + matching + "; "
                    + captured(expected, events));
        }
        return matching > 0 ? Optional.empty() : Optional.of("expected " + describe(expected)
                + " but none was logged; " + captured(expected, events));
    }

    private static boolean matches(LogExpectation expected, LogEvent event) {
        return belongsTo(event, expected.logger()) && event.level() == expected.level()
                && (expected.contains() == null || event.message().contains(expected.contains()));
    }

    private static boolean belongsTo(LogEvent event, String logger) {
        return event.logger().equals(logger) || event.logger().startsWith(logger + ".");
    }

    private static String describe(LogExpectation expected) {
        return expected.level() + " event of " + expected.logger()
                + (expected.contains() == null ? "" : " containing \"" + expected.contains() + "\"");
    }

    private static String captured(LogExpectation expected, List<LogEvent> events) {
        List<LogEvent> ofLogger = events.stream().filter(event -> belongsTo(event, expected.logger())).toList();
        return "captured events of " + expected.logger() + ": " + (ofLogger.isEmpty() ? "none"
                : abbreviate(ofLogger.toString()));
    }
}
