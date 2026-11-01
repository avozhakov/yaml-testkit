package com.yamltestkit.selftest.unit;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EnrichmentChain {

    private final Enricher enricher;

    /** True when the enricher hands back the very instance it got. */
    public boolean passesSameInstance(String requestId) {
        RequestContext context = new RequestContext(requestId);
        return enricher.enrich(context).get() == context;
    }
}
