package com.yamltestkit.selftest.app.ads;

import org.springframework.http.HttpStatus;

public record RenderedAds(HttpStatus status, String body) {
}
