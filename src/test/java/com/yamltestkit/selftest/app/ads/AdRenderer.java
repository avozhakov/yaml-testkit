package com.yamltestkit.selftest.app.ads;

import com.yamltestkit.selftest.app.ads.AdsResponse.AdView;
import com.yamltestkit.selftest.app.ads.AdsResponse.DiagEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Renders the ad response to the JSON string that is both the HTTP body and the logged response event's payload.
 * {@code qt} is rendered per ad only on v2.1 with a JSON-array {@code qt}; {@code advertiserUrl} only on the privacy
 * endpoint.
 */
@Component
@RequiredArgsConstructor
public class AdRenderer {

    private static final String MULTI_QT_VERSION = "2.1";

    private final JsonMapper jsonMapper;

    public RenderedAds render(AdContext context, AdSelection selection) {
        List<AdView> ads = selection.ads().stream().map(ad -> view(context, ad, selection.clickUrls())).toList();
        AdsResponse response = new AdsResponse(context.requestId(), ads, selection.warnings(),
                context.query().debug() ? diag(selection) : null);
        return new RenderedAds(HttpStatus.OK, jsonMapper.writeValueAsString(response));
    }

    public RenderedAds rejected(String error) {
        return new RenderedAds(HttpStatus.BAD_REQUEST, jsonMapper.writeValueAsString(new AdsError(error)));
    }

    private static AdView view(AdContext context, SelectedAd selected, Map<Long, String> clickUrls) {
        DspAd ad = selected.ad();
        AdQuery query = context.query();
        boolean renderQt = MULTI_QT_VERSION.equals(query.version()) && context.multiQt();
        return new AdView(selected.adSelectionId(), ad.id(), ad.title(), renderQt ? ad.qt() : null, ad.section(),
                ad.price(), ad.asin(), ad.gtin(), clickUrls.get(selected.adSelectionId()),
                query.privacy() ? ad.landingUrl() : null);
    }

    private List<DiagEntry> diag(AdSelection selection) {
        return List.of(new DiagEntry("dedup", json(Map.of("dropped", selection.droppedIds()))),
                new DiagEntry("serve", json(Map.of("served", selection.ads().size()))));
    }

    private String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }
}
