package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * One {@code expect.logs} entry: events of {@code logger} (and its descendants) at {@code level}.
 *
 * @param contains substring of the formatted message, {@code null} for any message
 * @param times    exact number of matching events, {@code null} for at least one
 * @param absent   no matching event may occur
 */
public record LogExpectation(String logger, LogLevel level, @Nullable String contains, @Nullable Integer times,
                             boolean absent) {

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
}
