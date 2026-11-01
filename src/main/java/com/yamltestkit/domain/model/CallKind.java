package com.yamltestkit.domain.model;

/**
 * The three kinds of {@code call} a case can make. {@link #CLASS} calls never start a Spring context.
 */
public enum CallKind {
    HTTP,
    BEAN,
    CLASS
}
