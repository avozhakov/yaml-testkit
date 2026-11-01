package com.yamltestkit.selftest.app.ads;

import java.util.List;
import java.util.Map;

/**
 * The ads to serve and what happened on the way.
 *
 * @param clickUrls  click URLs by ad selection id
 * @param warnings   why ads are missing (enrichment, provider, DSP or tracking failure)
 * @param droppedIds ids of DSP ads dropped as duplicates
 */
public record AdSelection(List<SelectedAd> ads, Map<Long, String> clickUrls, List<String> warnings,
                          List<String> droppedIds) {

    static AdSelection rejected(String warning) {
        return new AdSelection(List.of(), Map.of(), List.of(warning), List.of());
    }
}
