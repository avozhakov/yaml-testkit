package com.yamltestkit.selftest.app.discover;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DiscoverResponse(String version, @JsonProperty("request_id") String requestId, List<Item> items) {

    public record Item(String id, String title) {
    }
}
