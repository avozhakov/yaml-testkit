package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A {@code mockBeans} entry of {@code _context.yaml}: the bean of this name becomes a Mockito mock.
 *
 * @param type    the type to create the bean from when no definition with this name exists, {@code null} when not
 *                given
 * @param primary mark the bean primary; a replaced definition that already is primary stays primary
 * @param stubs   boot-time stubs, active from the moment the mock is created
 */
public record MockBean(String name, @Nullable String type, boolean primary, List<Stub> stubs) {

    public MockBean {
        stubs = List.copyOf(stubs);
    }
}
