package com.yamltestkit.selftest.app.ads;

import java.util.List;

/** @param multi {@code true} when the query term was a JSON array of terms */
public record QueryTerms(List<String> values, boolean multi) {
}
