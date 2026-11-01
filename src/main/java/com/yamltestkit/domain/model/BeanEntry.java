package com.yamltestkit.domain.model;

import java.util.List;

/**
 * A bean referenced by name in {@code mocks} / {@code spies}: a bare name has no stubs, a stub entry has one.
 */
public record BeanEntry(String bean, List<Stub> stubs) {

    public BeanEntry {
        stubs = List.copyOf(stubs);
    }
}
