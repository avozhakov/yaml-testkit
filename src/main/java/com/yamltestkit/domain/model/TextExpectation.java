package com.yamltestkit.domain.model;

import java.util.List;
import java.util.Map;

/**
 * {@code expect.bodyText}: string matchers on the raw body, each with one or more values.
 */
public record TextExpectation(Map<TextOperator, List<String>> matchers) {

    public TextExpectation {
        matchers = Map.copyOf(matchers);
    }
}
