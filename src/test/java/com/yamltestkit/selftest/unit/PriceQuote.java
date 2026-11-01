package com.yamltestkit.selftest.unit;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PriceQuote {

    private final TaxPolicy taxPolicy;

    public double quote(double net, String region) {
        return taxPolicy.gross(net, region);
    }
}
