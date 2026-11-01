package com.yamltestkit.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Properties and bean entries of the base of {@code _context.yaml} or of one of its {@code variants}; a variant adds
 * to the base or overrides it entry by entry (property key, bean name).
 *
 * @param properties Spring properties as written, {@code ${wiremock.*}} placeholders unresolved; a YAML list is
 *                   flattened into indexed keys ({@code key[0]}, {@code key[1]}, …)
 */
public record ContextVariant(Map<String, String> properties, List<MockBean> mockBeans, List<SpyBean> spyBeans,
                             List<ValueBean> valueBeans) {

    public static final ContextVariant EMPTY = new ContextVariant(Map.of(), List.of(), List.of(), List.of());

    public ContextVariant {
        properties = Map.copyOf(properties);
        mockBeans = List.copyOf(mockBeans);
        spyBeans = List.copyOf(spyBeans);
        valueBeans = List.copyOf(valueBeans);
    }
}
