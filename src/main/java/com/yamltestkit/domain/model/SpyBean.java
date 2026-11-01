package com.yamltestkit.domain.model;

import java.util.List;

/**
 * A {@code spyBeans} entry of {@code _context.yaml}: the bean of this name is wrapped in a Mockito spy.
 *
 * @param stubs boot-time stubs, active from the moment the spy is created
 */
public record SpyBean(String name, List<Stub> stubs) {

    public SpyBean {
        stubs = List.copyOf(stubs);
    }
}
