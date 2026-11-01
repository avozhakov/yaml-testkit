package com.yamltestkit.selftest.app.text;

import com.yamltestkit.selftest.app.FeedCache;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Non-JSON and golden bodies: {@code GET /text} lists the cached feeds as XML-ish text (imitates the ALS / XML
 * renderings), {@code 400} with a plain-text reason without {@code qt}; {@code GET /golden} returns the fixed fallback
 * response (imitates the DSP-only fallback golden).
 */
@RestController
@RequiredArgsConstructor
public class TextController {

    private final FeedCache feedCache;
    private final TextRenderer renderer;

    @GetMapping("/text")
    public ResponseEntity<String> text(@RequestParam(required = false) @Nullable String qt) {
        if (qt == null || qt.isBlank()) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("qt is required");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(renderer.feedListing(qt, feedCache.rows()));
    }

    @GetMapping(path = "/golden", produces = MediaType.APPLICATION_JSON_VALUE)
    public String golden() {
        return renderer.fallback();
    }
}
