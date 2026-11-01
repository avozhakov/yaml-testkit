package com.yamltestkit.selftest.unit;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class ProviderService {

    private final ProviderRegistry registry;
    private final ProviderLookup lookup;

    public String describe(String name) {
        return registry.find(name).map(provider -> provider.name() + ":" + provider.maxAds()).orElse("none");
    }

    public int fetchMaxAds(String name) {
        return lookup.fetch(name).join().maxAds();
    }

    public CompletableFuture<Provider> fetch(String name) {
        return lookup.fetch(name);
    }
}
