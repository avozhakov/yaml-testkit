package com.yamltestkit.selftest.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Collects the {@link Feature} beans by bean name (imitates the registries {@code ReservedTestSourceKeyIntegrationTest}
 * reads keys from): {@code optionalFeature} is a key only while {@code fixture.optional-feature} is {@code on}.
 */
@Component
@RequiredArgsConstructor
public class FeatureRegistry {

    private final Map<String, Feature> features;

    public List<String> keys() {
        return features.keySet().stream().sorted().toList();
    }
}
