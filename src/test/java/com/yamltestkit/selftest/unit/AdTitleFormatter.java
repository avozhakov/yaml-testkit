package com.yamltestkit.selftest.unit;

import lombok.RequiredArgsConstructor;

import java.util.List;

/** A unit with a mocked collaborator and a value dependency, matched by parameter name or type. */
@RequiredArgsConstructor
public class AdTitleFormatter {

    private final CtaIdConverter ctaIds;
    private final Limits limits;

    public FormattedAds format(String cta, List<String> titles) {
        return new FormattedAds(ctaIds.convert(cta), titles.stream().limit(limits.max()).toList());
    }
}
