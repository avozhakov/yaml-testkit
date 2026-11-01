package com.yamltestkit.domain.model;

/**
 * Operators of {@code expect.bodyText}.
 */
public enum TextOperator {
    EQUALS("equals"),
    CONTAINS("contains"),
    NOT_CONTAINS("notContains"),
    STARTS_WITH("startsWith"),
    MATCHES("matches");

    private final String yamlName;

    TextOperator(String yamlName) {
        this.yamlName = yamlName;
    }

    public String yamlName() {
        return yamlName;
    }
}
