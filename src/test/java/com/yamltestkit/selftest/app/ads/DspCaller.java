package com.yamltestkit.selftest.app.ads;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.yamltestkit.selftest.app.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds the DSP request from the context and calls the DSP, retrying an {@link ApiException}
 * {@code fixture.dsp.retries} times; when every attempt fails the request degrades to no ads with a warning that names
 * the last failure (imitates the tracking-URL stage's resilience).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DspCaller {

    private static final String COMPETITIVENESS_ID = "competitivenessId";

    private final DspClient dspClient;
    private final DspProperties properties;

    public DspResult requestAds(AdContext context, int maxAds) {
        AdRequest request = adRequest(context, maxAds);
        int attempts = properties.retries() + 1;
        ApiException failure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return DspResult.served(dspClient.requestAds(request).getAds());
            } catch (ApiException e) {
                log.warn("DSP attempt {} of {} failed with code {}", attempt, attempts, e.getCode());
                failure = e;
            }
        }
        return DspResult.failed(describe(failure));
    }

    private static AdRequest adRequest(AdContext context, int maxAds) {
        Struct placement = context.placement();
        return AdRequest.builder()
                .requestId(context.requestId())
                .queryTerm(context.query().queryTerm())
                .queryTerms(context.terms())
                .maxAds(maxAds)
                .publisher(new Publisher(context.query().placementId(), competitivenessId(placement)))
                .build();
    }

    private static @Nullable String competitivenessId(Struct placement) {
        return placement.containsFields(COMPETITIVENESS_ID)
                ? placement.getFieldsOrDefault(COMPETITIVENESS_ID, Value.getDefaultInstance()).getStringValue()
                : null;
    }

    private static String describe(ApiException failure) {
        Throwable cause = failure.getCause();
        return "dsp failed: " + failure.getCode() + " " + failure.getMessage()
                + (cause == null ? "" : " caused by " + cause.getClass().getSimpleName() + "(" + cause.getMessage() + ")");
    }
}
