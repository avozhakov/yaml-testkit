package com.yamltestkit.selftest.unit;

import java.util.concurrent.CompletableFuture;

public interface ProviderLookup {

    CompletableFuture<Provider> fetch(String name);
}
