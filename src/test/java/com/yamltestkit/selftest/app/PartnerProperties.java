package com.yamltestkit.selftest.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * @param retries how many times a failed partner search is retried (5xx status, I/O error or timeout)
 */
@ConfigurationProperties("partner")
public record PartnerProperties(URI baseUrl, Duration timeout, int retries) {
}
