package com.yamltestkit.selftest.unit;

/** A class with two constructors: a case must choose one with {@code constructor}. */
public class Greeter {

    private static final String DEFAULT_GREETING = "Hello";

    private final String greeting;

    public Greeter() {
        this(DEFAULT_GREETING);
    }

    public Greeter(String greeting) {
        this.greeting = greeting;
    }

    public String greet(String name) {
        return greeting + ", " + name;
    }

    public String greet(String name, int times) {
        return (greeting + ", " + name + "! ").repeat(times).strip();
    }
}
