package com.yamltestkit.selftest.app.ads;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** Looks up an ad provider by name (imitates the feed provider registries); the cases mock it. */
@Component
public class ProviderRegistry {

    private static final Map<String, Provider> PROVIDERS = Map.of("dsp", new Provider("dsp", 4));

    public Optional<Provider> find(String name) {
        return Optional.ofNullable(PROVIDERS.get(name));
    }
}
