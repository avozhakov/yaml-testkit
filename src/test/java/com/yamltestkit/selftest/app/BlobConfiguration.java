package com.yamltestkit.selftest.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** A generic {@code @Bean} standing in for a Redis connection ({@code StatefulRedisClusterConnection<String, byte[]>}). */
@Configuration(proxyBeanMethods = false)
public class BlobConfiguration {

    @Bean
    public Supplier<Map<String, byte[]>> blobSupplier() {
        return () -> {
            throw new IllegalStateException("no Redis in tests");
        };
    }

    @Bean
    public ExchangeSources exchangeSources() {
        return new ExchangeSources(List.of("a", "b"));
    }
}
