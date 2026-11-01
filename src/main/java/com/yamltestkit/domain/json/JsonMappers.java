package com.yamltestkit.domain.json;

import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * The plain Jackson mapper the testkit shares for reading and writing JSON trees (payload files, response bodies,
 * JSONPath reads, protobuf output). The value converters keep their own, configured mapper.
 */
public final class JsonMappers {

    private static final JsonMapper PLAIN = JsonMapper.builder().build();

    private JsonMappers() {
    }

    public static JsonMapper plain() {
        return PLAIN;
    }
}
