package com.yamltestkit.selftest.app.ads;

import java.util.List;

/** The DSP's ads, or no ads and the reason when every attempt failed. */
public record DspResult(List<DspAd> ads, List<String> warnings) {

    static DspResult served(List<DspAd> ads) {
        return new DspResult(ads, List.of());
    }

    static DspResult failed(String warning) {
        return new DspResult(List.of(), List.of(warning));
    }
}
