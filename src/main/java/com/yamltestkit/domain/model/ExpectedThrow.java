package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * {@code expect.throws} of a {@code bean} / {@code class} call.
 */
public record ExpectedThrow(String type, @Nullable String message) {
}
