package com.yamltestkit.selftest.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * A bean that exists only while {@code fixture.optional-feature} is {@code on} (imitates the eBay direct-path beans):
 * {@code _defaults.yaml} mocks it, and the {@code eco} variant, which turns it off, must still start.
 */
@Component
@ConditionalOnProperty(name = "fixture.optional-feature", havingValue = "on")
public class OptionalFeature implements Feature {

    @Override
    public String name() {
        return "real";
    }
}
