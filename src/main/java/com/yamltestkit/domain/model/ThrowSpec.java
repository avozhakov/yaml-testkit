package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An exception to construct: by the constructor matching {@code args}, or {@code message}, plus an optional cause
 * built the same way.
 */
public record ThrowSpec(String type, @Nullable String message, List<JsonNode> args, @Nullable ThrowSpec cause) {

    public ThrowSpec {
        args = List.copyOf(args);
    }
}
