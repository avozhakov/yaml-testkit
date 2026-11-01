package com.yamltestkit.selftest.unit;

import io.vavr.control.Validation;

/** Imitates request enrichers, stubbed as pass-through with {@code returnsArg}. */
public interface Enricher {

    Validation<String, RequestContext> enrich(RequestContext context);
}
