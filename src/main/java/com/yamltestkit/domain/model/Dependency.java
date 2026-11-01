package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * How a {@code call.class} constructor parameter listed in {@code deps} is resolved.
 */
public sealed interface Dependency permits Dependency.Stubbed, Dependency.Value, Dependency.Real {

    /** A Mockito mock with these stubs. */
    record Stubbed(List<Stub> stubs) implements Dependency {

        public Stubbed {
            stubs = List.copyOf(stubs);
        }
    }

    /** A real object converted from the YAML value. */
    record Value(JsonNode value) implements Dependency {
    }

    /** An instance of the parameter's type, built by the same rules as the unit itself. */
    record Real() implements Dependency {
    }
}
