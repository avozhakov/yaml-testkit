package com.yamltestkit.selftest.app.ads;

import com.yamltestkit.selftest.app.ApiException;
import com.yamltestkit.selftest.app.tracking.TrackingClient;
import com.yamltestkit.selftest.app.tracking.TrackingItem;
import io.vavr.control.Validation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gets a click URL per ad from the tracking service (imitates the tracking-URL stage): with no ads the service is not
 * called; a failed call is returned as the reason, so the caller can drop the ads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdTracker {

    private final TrackingClient trackingClient;

    public Validation<String, Map<Long, String>> track(List<SelectedAd> ads) {
        if (ads.isEmpty()) {
            return Validation.valid(Map.of());
        }
        try {
            Map<String, String> urls = trackingClient.clickUrls(ads.stream()
                    .map(ad -> new TrackingItem(String.valueOf(ad.adSelectionId()), ad.ad().landingUrl()))
                    .toList());
            return Validation.valid(ads.stream()
                    .filter(ad -> urls.containsKey(String.valueOf(ad.adSelectionId())))
                    .collect(Collectors.toMap(SelectedAd::adSelectionId,
                            ad -> urls.get(String.valueOf(ad.adSelectionId())))));
        } catch (ApiException e) {
            log.warn("tracking failed with code {}", e.getCode());
            return Validation.invalid("tracking failed: " + e.getCode() + " " + e.getMessage());
        }
    }
}
