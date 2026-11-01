package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.plan.VariantKey;
import com.yamltestkit.domain.plan.VariantPlan;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The application contexts and WireMock servers of a run. Each glob root's WireMock servers start before its first
 * context; a variant's shared context starts when its first case needs it and is reused by its other cases; a
 * fresh context is started for one case and closed by it. A variant whose context failed to start keeps the failure
 * and reports it for each of its cases without retrying. {@link #close()} closes every shared context and server.
 * Not thread-safe: cases run sequentially.
 */
@RequiredArgsConstructor
public final class ContextRegistry implements AutoCloseable {

    private final ContextStarter starter;
    private final Map<String, WireMockServices> wireMocks = new LinkedHashMap<>();
    private final Map<VariantKey, RunningContext> shared = new LinkedHashMap<>();
    private final Map<VariantKey, IllegalStateException> failures = new LinkedHashMap<>();

    /** The WireMock servers of the plan's glob root, started on first use. */
    public WireMockServices wireMock(VariantPlan plan) {
        RootConfiguration root = plan.root();
        return wireMocks.computeIfAbsent(root.root(),
                ignored -> WireMockServices.start(plan.context().services(), plan.context().label()));
    }

    /** The variant's shared context if it is running. */
    public Optional<RunningContext> running(VariantKey key) {
        return Optional.ofNullable(shared.get(key));
    }

    /**
     * The variant's shared context, started if it is not running yet, or a newly started context of the variant
     * when {@code fresh}; the caller closes a fresh context.
     *
     * @throws IllegalStateException when the context fails to start, or failed to start before (shared only)
     */
    public Acquired acquire(VariantPlan plan, boolean fresh) {
        if (fresh) {
            return new Acquired(start(plan), true);
        }
        RunningContext running = shared.get(plan.key());
        if (running != null) {
            return new Acquired(running, false);
        }
        IllegalStateException failure = failures.get(plan.key());
        if (failure != null) {
            throw new IllegalStateException(failure.getMessage(), failure.getCause());
        }
        try {
            RunningContext started = start(plan);
            shared.put(plan.key(), started);
            return new Acquired(started, true);
        } catch (IllegalStateException e) {
            failures.put(plan.key(), e);
            throw e;
        }
    }

    @Override
    public void close() {
        List<RuntimeException> errors = new ArrayList<>();
        shared.values().forEach(context -> closeQuietly(context, errors));
        wireMocks.values().forEach(servers -> closeQuietly(servers, errors));
        shared.clear();
        wireMocks.clear();
        if (!errors.isEmpty()) {
            IllegalStateException failure = new IllegalStateException("closing the YAML Testkit contexts failed",
                    errors.getFirst());
            errors.stream().skip(1).forEach(failure::addSuppressed);
            throw failure;
        }
    }

    /** Wraps any startup failure, so that each case of the variant reports it with the variant's name. */
    private RunningContext start(VariantPlan plan) {
        try {
            return starter.start(plan, wireMock(plan));
        } catch (RuntimeException e) {
            throw new IllegalStateException(plan.key().describe() + ": the application context failed to start: "
                    + e.getMessage(), e);
        }
    }

    /** Collects the failure so that one broken context doesn't keep the others open. */
    private static void closeQuietly(AutoCloseable resource, List<RuntimeException> errors) {
        try {
            resource.close();
        } catch (Exception e) {
            errors.add(e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e));
        }
    }

    /**
     * @param started the context was started by this call
     */
    public record Acquired(RunningContext context, boolean started) {
    }
}
