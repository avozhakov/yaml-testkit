package com.yamltestkit.selftest.app.discover;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The {@code POST /discover} body, in the snake_case wire format of Discover 3.0; unknown fields are accepted. */
public record DiscoverRequest(Intent intent, Meta meta) {

    public record Intent(String prompt, @JsonProperty("ad_count") int adCount) {
    }

    public record Meta(@JsonProperty("request_id") String requestId) {
    }
}
