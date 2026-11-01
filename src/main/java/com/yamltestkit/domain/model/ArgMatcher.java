package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Matcher for one argument of a stub or a verification.
 */
public sealed interface ArgMatcher permits ArgMatcher.Literal, ArgMatcher.Any, ArgMatcher.OfType {

    /** YAML spelling of {@link Any}. */
    String ANY_TOKEN = "#any";

    /** Equal to this value by JSON equality after conversion. */
    record Literal(JsonNode value) implements ArgMatcher {
    }

    /** {@code "#any"}: any value, {@code null} included. */
    record Any() implements ArgMatcher {
    }

    /** {@code { anyOf: <FQN> }}: any instance of the type. */
    record OfType(String type) implements ArgMatcher {
    }
}
