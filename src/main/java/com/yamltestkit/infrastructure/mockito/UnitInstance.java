package com.yamltestkit.infrastructure.mockito;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The object a {@code call.class} step calls ({@code null} for a static method) and the Mockito mocks given to its
 * constructor, keyed by {@code deps} key, or by parameter name for parameters not listed in {@code deps}; these are
 * the targets of {@code verify} checks on {@code dep}.
 */
public record UnitInstance(@Nullable Object instance, Map<String, Object> mocks) {

    public static final UnitInstance STATIC = new UnitInstance(null, Map.of());

    public UnitInstance {
        mocks = Map.copyOf(mocks);
    }
}
