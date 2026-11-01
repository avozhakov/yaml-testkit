package com.yamltestkit.infrastructure.logback;

import com.yamltestkit.domain.model.LogExpectation.LogLevel;

/**
 * A captured log event, independent of the logging backend.
 *
 * @param message the formatted message, arguments filled in
 */
record LogEvent(String logger, LogLevel level, String message) {

    @Override
    public String toString() {
        return level + " " + logger + ": " + message;
    }
}
