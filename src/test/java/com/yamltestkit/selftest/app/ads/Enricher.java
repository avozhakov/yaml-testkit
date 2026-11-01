package com.yamltestkit.selftest.app.ads;

import io.vavr.control.Validation;

/** Imitates a request context enricher: returns the next context, or why the request can't be served. */
public interface Enricher {

    Validation<String, AdContext> enrich(AdContext context);
}
