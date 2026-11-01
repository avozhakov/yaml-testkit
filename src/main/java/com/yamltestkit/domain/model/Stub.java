package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A method stub, shared by {@code mocks}, {@code spies}, boot-time {@code stubs} and {@code deps.<name>.stubs}.
 *
 * @param args per-argument matchers, {@code null} when the YAML gives none (any arguments match)
 */
public record Stub(MethodRef method, @Nullable List<ArgMatcher> args, StubAnswer answer) {

    public Stub {
        args = args == null ? null : List.copyOf(args);
    }
}
