package com.yamltestkit.selftest.app.ads;

import java.util.List;

/**
 * The DSP's answer: a hand-written {@code builder().setX()} class without a no-argument constructor, like the
 * generated {@code AdResponse} of a client library. The builder style is the point of this fixture, so it is written
 * by hand.
 */
public final class DspResponse {

    private final List<DspAd> ads;

    private DspResponse(Builder builder) {
        this.ads = List.copyOf(builder.ads);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<DspAd> getAds() {
        return ads;
    }

    public static final class Builder {

        private List<DspAd> ads = List.of();

        public Builder setAds(List<DspAd> ads) {
            this.ads = ads;
            return this;
        }

        public DspResponse build() {
            return new DspResponse(this);
        }
    }
}
