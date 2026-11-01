package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A method by name, with {@code paramTypes} selecting an overload; {@code null} when the YAML gives none.
 */
public record MethodRef(String name, @Nullable List<String> paramTypes) {

    public MethodRef {
        paramTypes = paramTypes == null ? null : List.copyOf(paramTypes);
    }
}
