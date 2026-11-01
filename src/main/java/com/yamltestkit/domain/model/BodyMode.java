package com.yamltestkit.domain.model;

import java.util.Arrays;

/**
 * How expected and actual JSON are compared.
 */
public enum BodyMode {
    /** Extra actual fields allowed, array order significant (the default). */
    LENIENT("lenient"),
    /** No extra fields, array order significant. */
    STRICT("strict"),
    /** Raw text equality. */
    EXACT("exact");

    private final String yamlName;

    BodyMode(String yamlName) {
        this.yamlName = yamlName;
    }

    public static BodyMode fromYaml(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.yamlName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown body mode: " + value));
    }
}
