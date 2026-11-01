package com.yamltestkit.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Query of an HTTP call: parameters the engine encodes, or a raw string sent as written.
 */
public sealed interface Query permits Query.Params, Query.Raw {

    Query NONE = new Params(Map.of());

    /** Parameter values in YAML order; a YAML list repeats the parameter. */
    record Params(Map<String, List<String>> values) implements Query {
    }

    record Raw(String value) implements Query {
    }
}
