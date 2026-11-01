package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The {@code call} of a step: exactly one of {@code http}, {@code bean}, {@code class}. Payload values are already
 * resolved from {@code { file }}; an absent body is a {@code MissingNode}, a YAML {@code null} a {@code NullNode}.
 */
public sealed interface Call permits Call.Http, Call.Bean, Call.Unit {

    CallKind kind();

    /** HTTP request to the running application. */
    record Http(String method, String path, Query query, Map<String, String> headers, JsonNode body) implements Call {

        public Http {
            headers = Map.copyOf(headers);
        }

        @Override
        public CallKind kind() {
            return CallKind.HTTP;
        }
    }

    /** Method call on a bean of the application context. */
    record Bean(String bean, MethodRef method, List<JsonNode> args) implements Call {

        public Bean {
            args = List.copyOf(args);
        }

        @Override
        public CallKind kind() {
            return CallKind.BEAN;
        }
    }

    /**
     * {@code call.class}: method call on an instance the engine builds without Spring.
     *
     * @param constructor parameter types of the chosen constructor, {@code null} when not given
     * @param deps        constructor dependencies by parameter name or type, in YAML order
     */
    record Unit(
            String className,
            MethodRef method,
            List<JsonNode> args,
            @Nullable List<String> constructor,
            Map<String, Dependency> deps
    ) implements Call {

        public Unit {
            args = List.copyOf(args);
            constructor = constructor == null ? null : List.copyOf(constructor);
        }

        @Override
        public CallKind kind() {
            return CallKind.CLASS;
        }
    }
}
