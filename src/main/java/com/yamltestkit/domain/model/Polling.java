package com.yamltestkit.domain.model;

import java.time.Duration;

/**
 * {@code expect.within} / {@code every}: repeat the step's call and expectations until they pass or time runs out.
 */
public record Polling(Duration within, Duration every) {

    public static final Duration DEFAULT_EVERY = Duration.ofMillis(200);
}
