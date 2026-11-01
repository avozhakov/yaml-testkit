package com.yamltestkit.selftest.unit;

import lombok.RequiredArgsConstructor;

/** A concrete collaborator built with {@code real: true}; its own {@link RateSource} becomes a plain mock. */
@RequiredArgsConstructor
public class TaxPolicy {

    private final RateSource rates;

    public double gross(double net, String region) {
        return net * (1 + rates.rate(region));
    }
}
