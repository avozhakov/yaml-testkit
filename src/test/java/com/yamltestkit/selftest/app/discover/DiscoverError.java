package com.yamltestkit.selftest.app.discover;

import org.jspecify.annotations.Nullable;

/** The error body of {@code /discover}: the requested version (when sent) and a published error code. */
public record DiscoverError(@Nullable String version, Error error) {

    public record Error(String code, String message) {
    }
}
