package com.yamltestkit.domain.model;

import java.util.List;

/**
 * {@code expect.context}: bean names and bean types that must be present in, or absent from, the case's context.
 */
public record ContextExpectation(Presence beans, Presence beanTypes) {

    public record Presence(List<String> present, List<String> absent) {

        public static final Presence NONE = new Presence(List.of(), List.of());

        public Presence {
            present = List.copyOf(present);
            absent = List.copyOf(absent);
        }
    }
}
