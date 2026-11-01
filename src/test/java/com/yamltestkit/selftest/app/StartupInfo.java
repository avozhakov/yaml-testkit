package com.yamltestkit.selftest.app;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts calls per instance, so cases can tell a reused context from a newly started one. Every context of the
 * fixture creates exactly one instance, so the JVM-wide count of created instances is the number of contexts started,
 * which {@code YamlCaseEngineIT} reads before and after a run.
 */
@Component
public class StartupInfo {

    private static final AtomicInteger CREATED = new AtomicInteger();

    private final AtomicInteger hits = new AtomicInteger();

    public StartupInfo() {
        CREATED.incrementAndGet();
    }

    /** How many fixture contexts the JVM has started so far. */
    public static int created() {
        return CREATED.get();
    }

    public int hit() {
        return hits.incrementAndGet();
    }
}
