package com.yamltestkit.selftest.app.ads;

import com.google.protobuf.Struct;

import java.util.List;

/**
 * Imitates a request context: the data an ad request carries through the enrichers.
 *
 * @param terms   the query terms left after intent filtering
 * @param multiQt {@code true} when {@code qt} was a JSON array of terms
 */
public record AdContext(String requestId, AdQuery query, List<String> terms, boolean multiQt, Struct placement) {
}
