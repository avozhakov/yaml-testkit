package com.yamltestkit.selftest.app.ads;

/** A DSP ad chosen for the response, under the ad selection id generated for it. */
public record SelectedAd(long adSelectionId, DspAd ad) {
}
