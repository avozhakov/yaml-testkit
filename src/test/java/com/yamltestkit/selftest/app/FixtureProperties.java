package com.yamltestkit.selftest.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Properties whose precedence the cases check: configuration file, {@code _context.yaml}, variant.
 *
 * @param mode   {@code fixture.mode}, set by every layer
 * @param source {@code fixture.source}, set only by the configuration file
 */
@ConfigurationProperties("fixture")
public record FixtureProperties(String mode, @DefaultValue("none") String source) {
}
