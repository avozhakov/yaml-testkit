package com.yamltestkit.selftest.app;

/** Body of the {@code POST /search} request sent to the partner. */
public record PartnerSearch(String query, int limit) {
}
