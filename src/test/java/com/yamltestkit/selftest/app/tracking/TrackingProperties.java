package com.yamltestkit.selftest.app.tracking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Where the tracking service runs: {@code _context.yaml} points host and port at the {@code tracking} WireMock server. */
@ConfigurationProperties("tracking")
public record TrackingProperties(String host, int port, Duration timeout) {
}
