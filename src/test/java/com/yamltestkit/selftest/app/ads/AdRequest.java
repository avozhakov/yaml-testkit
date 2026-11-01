package com.yamltestkit.selftest.app.ads;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The request sent to the DSP: a Lombok {@code @Builder} class without a no-argument constructor, like a request
 * class of a client library that tests capture. {@code queryTerm} is the {@code qt} as received, {@code queryTerms}
 * the terms left after splitting and intent filtering.
 */
@Getter
@Builder(toBuilder = true)
public class AdRequest {

    private final String requestId;
    private final String queryTerm;
    private final List<String> queryTerms;
    private final int maxAds;
    private final Publisher publisher;
}
