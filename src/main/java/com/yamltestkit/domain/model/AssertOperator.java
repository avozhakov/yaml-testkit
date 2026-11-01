package com.yamltestkit.domain.model;

/**
 * Value operators of an {@code assert} entry ({@code all} / {@code none} are nested assertions, see
 * {@link Assertion}).
 */
public enum AssertOperator {
    EQUALS("equals"),
    NOT_EQUALS("notEquals"),
    IS_NULL("isNull"),
    NOT_NULL("notNull"),
    EXISTS("exists"),
    ABSENT("absent"),
    SIZE("size"),
    GTE("gte"),
    LTE("lte"),
    CONTAINS("contains"),
    CONTAINS_EXACTLY("containsExactly"),
    CONTAINS_IN_ANY_ORDER("containsInAnyOrder"),
    UNIQUE("unique"),
    STARTS_WITH("startsWith"),
    MATCHES("matches");

    private final String yamlName;

    AssertOperator(String yamlName) {
        this.yamlName = yamlName;
    }

    public String yamlName() {
        return yamlName;
    }
}
