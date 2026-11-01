package com.yamltestkit.selftest.app.ads;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The ad response body. Optional fields are omitted when {@code null}, except {@code gtin}, which is always written
 * (as {@code null} when the DSP sent none), so the cases can tell an absent key from a {@code null} value.
 *
 * @param diag diagnostics, only with {@code debug=true}; each payload is JSON inside a string
 */
public record AdsResponse(String requestId, List<AdView> ads, List<String> warnings,
                          @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<DiagEntry> diag) {

    /**
     * @param qt            the DSP's term, only on v2.1 with a JSON-array {@code qt}
     * @param advertiserUrl only on the privacy endpoint
     */
    public record AdView(long adSelectionId, String id, String title,
                         @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String qt,
                         String section, int price,
                         @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String asin,
                         @Nullable String gtin,
                         @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String clickUrl,
                         @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String advertiserUrl) {
    }

    public record DiagEntry(String step, String payload) {
    }
}
