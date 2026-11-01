package com.yamltestkit.selftest.app.ads;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /ads} and the privacy twin {@code GET /ads-private} (imitate {@code /is} and {@code /isp}). */
@RestController
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;

    @GetMapping(path = "/ads", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> ads(@RequestParam long plid, @RequestParam(defaultValue = "") String qt,
                                      @RequestParam(defaultValue = "2.0") String v,
                                      @RequestParam(defaultValue = "dsp") String provider,
                                      @RequestParam(defaultValue = "false") boolean debug) {
        return respond(adService.serve(new AdQuery("/ads", plid, qt, v, provider, debug, false)));
    }

    @GetMapping(path = "/ads-private", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> privateAds(@RequestParam long plid, @RequestParam(defaultValue = "") String qt,
                                             @RequestParam(defaultValue = "2.0") String v,
                                             @RequestParam(defaultValue = "dsp") String provider,
                                             @RequestParam(defaultValue = "false") boolean debug) {
        return respond(adService.serve(new AdQuery("/ads-private", plid, qt, v, provider, debug, true)));
    }

    private static ResponseEntity<String> respond(RenderedAds rendered) {
        return ResponseEntity.status(rendered.status()).body(rendered.body());
    }
}
