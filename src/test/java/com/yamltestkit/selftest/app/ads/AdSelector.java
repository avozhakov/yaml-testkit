package com.yamltestkit.selftest.app.ads;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.vavr.control.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chooses the ads of a request: enrichment, provider lookup, DSP call capped at
 * {@code min(placement.availableSlots, provider.maxAds)}, duplicates dropped by ad id (first kept), an ad selection id
 * per ad, and click URLs from the tracking service. A failed tracking call drops every ad.
 */
@Component
@RequiredArgsConstructor
public class AdSelector {

    private static final String AVAILABLE_SLOTS = "availableSlots";

    private final Enricher pricingEnricher;
    private final ProviderRegistry providerRegistry;
    private final DspCaller dspCaller;
    private final AdTracker adTracker;
    private final IdGenerator ids;

    public AdSelection select(AdContext context) {
        Validation<String, AdContext> enriched = pricingEnricher.enrich(context);
        if (enriched.isInvalid()) {
            return AdSelection.rejected("enrichment failed: " + enriched.getError());
        }
        String providerName = context.query().provider();
        return providerRegistry.find(providerName)
                .map(provider -> selectFrom(enriched.get(), provider))
                .orElseGet(() -> AdSelection.rejected("unknown provider " + providerName));
    }

    private AdSelection selectFrom(AdContext context, Provider provider) {
        int maxAds = Math.min(availableSlots(context.placement()), provider.maxAds());
        DspResult dsp = dspCaller.requestAds(context, maxAds);
        Map<String, DspAd> unique = new LinkedHashMap<>();
        List<String> dropped = new ArrayList<>();
        dsp.ads().forEach(ad -> {
            if (unique.putIfAbsent(ad.id(), ad) != null) {
                dropped.add(ad.id());
            }
        });
        List<SelectedAd> ads = unique.values().stream()
                .limit(maxAds)
                .map(ad -> new SelectedAd(ids.nextAdSelectionId(), ad))
                .toList();
        Validation<String, Map<Long, String>> clickUrls = adTracker.track(ads);
        if (clickUrls.isInvalid()) {
            List<String> warnings = new ArrayList<>(dsp.warnings());
            warnings.add(clickUrls.getError());
            return new AdSelection(List.of(), Map.of(), warnings, dropped);
        }
        return new AdSelection(ads, clickUrls.get(), dsp.warnings(), dropped);
    }

    private static int availableSlots(Struct placement) {
        return (int) placement.getFieldsOrDefault(AVAILABLE_SLOTS, Value.getDefaultInstance()).getNumberValue();
    }
}
