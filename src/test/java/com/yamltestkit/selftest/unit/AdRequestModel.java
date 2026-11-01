package com.yamltestkit.selftest.unit;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Lombok {@code @Builder} without a no-argument constructor, like the request models of a client library. */
@Getter
@Builder(toBuilder = true)
public class AdRequestModel {

    private final int maxAds;
    private final String publisherId;
    private final List<String> sections;
}
