package com.yamltestkit.selftest.app;

import java.util.List;

/** Body of the partner's {@code POST /search} response. */
public record PartnerSearchResult(List<PartnerItem> items) {
}
