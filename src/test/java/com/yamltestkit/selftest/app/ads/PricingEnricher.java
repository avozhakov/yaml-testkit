package com.yamltestkit.selftest.app.ads;

import io.vavr.control.Validation;
import org.springframework.stereotype.Component;

/**
 * Imitates {@code pricingEnricher}, which needs an external pricing service; the cases mock it as a pass-through
 * with {@code returnsArg}.
 */
@Component
public class PricingEnricher implements Enricher {

    @Override
    public Validation<String, AdContext> enrich(AdContext context) {
        return Validation.invalid("no pricing service in tests");
    }
}
