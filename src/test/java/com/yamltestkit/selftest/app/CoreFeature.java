package com.yamltestkit.selftest.app;

import org.springframework.stereotype.Component;

/** A feature present in every variant, so {@link FeatureRegistry} is never empty. */
@Component
public class CoreFeature implements Feature {

    @Override
    public String name() {
        return "core";
    }
}
