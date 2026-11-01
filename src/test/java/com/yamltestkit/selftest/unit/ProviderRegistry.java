package com.yamltestkit.selftest.unit;

import java.util.Optional;

public interface ProviderRegistry {

    Optional<Provider> find(String name);
}
