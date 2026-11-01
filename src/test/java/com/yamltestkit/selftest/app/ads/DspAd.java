package com.yamltestkit.selftest.app.ads;

import org.jspecify.annotations.Nullable;

/** An ad as the DSP returns it; {@code landingUrl} is sent to the tracking service and rendered as {@code advertiserUrl}. */
public record DspAd(String id, String title, @Nullable String qt, String section, int price, @Nullable String asin,
                    @Nullable String gtin, String landingUrl) {
}
