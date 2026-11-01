package com.yamltestkit.domain.model;

/**
 * One call of a case with the stubs it adds (active for this step and later ones) and its expectations.
 */
public record Step(String name, Call call, StubSet stubs, Expect expect) {

    /** Name of the single step of a case written with {@code call} instead of {@code steps}. */
    public static final String IMPLICIT_NAME = "main";
}
