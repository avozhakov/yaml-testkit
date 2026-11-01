package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Turns an expected value as written into the value to compare with: the value itself, or the earlier result a
 * {@code { ref }} points to.
 */
@FunctionalInterface
public interface ExpectedValueResolver {

    JsonNode resolve(JsonNode expected);
}
