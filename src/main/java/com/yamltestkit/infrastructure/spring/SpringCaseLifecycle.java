package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.domain.plan.ContextPlanner;
import com.yamltestkit.domain.plan.ContextPlans;
import com.yamltestkit.domain.plan.StubLayers;
import com.yamltestkit.domain.plan.VariantPlan;
import com.yamltestkit.infrastructure.logback.LogCapture;
import com.yamltestkit.infrastructure.mockito.StubApplier;
import com.yamltestkit.infrastructure.mockito.StubErrors;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prepares each {@code http} / {@code bean} case (design D6, D20): wait {@code settleMs} after the root's previous
 * case; reset the mocks and spies of the variant's running context and every WireMock server of the root, re-attach
 * the log capture (another context's start may have dropped it) and clear the captured log events (before the
 * context is acquired, so a context started now keeps what its startup stubbed); get or start the context; after a
 * shared context starts, wait {@code settleMs} for its warm-up work, then forget the calls, requests and log events
 * its startup made (a fresh context keeps them, so its case can check them); then register the stub layers boot-time
 * {@code stubs} → {@code _defaults.yaml} → file {@code defaults} → case. Each step's stubs are added on top before
 * the step runs. A bean only {@code _defaults.yaml} names that the context lacks has its stubs skipped.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class SpringCaseLifecycle implements AutoCloseable {

    private final ContextRegistry registry;
    private final StubApplier stubs;
    private final Map<String, RootConfiguration> roots;
    private final LogCapture logs;
    private final Pause pause;
    private final Set<String> rootsWithCases = new HashSet<>();
    private @Nullable ContextPlans plans;

    public static SpringCaseLifecycle create(ValueConverters converters, Map<String, RootConfiguration> roots,
                                             LogCapture logs) {
        StubApplier stubs = StubApplier.create(converters);
        return new SpringCaseLifecycle(new ContextRegistry(new ContextStarter(stubs, converters, logs)), stubs, roots,
                logs, Pause.SLEEP);
    }

    /** Plans the variants from every loaded case file; needed before {@link #open}. */
    public void prepare(List<CaseFile> files) {
        plans = ContextPlanner.plan(roots, files);
    }

    /**
     * @param errors collects errors the stubs raise while the case runs
     * @return the case's context; closing it closes a fresh context
     * @throws com.yamltestkit.domain.model.CaseDefinitionException when the case or its variant is invalid
     * @throws IllegalStateException                              when the variant's context fails to start, the
     *                                                            message starting with the case's location
     */
    public CaseContext open(YamlCase yamlCase, StubErrors errors) {
        if (plans == null) {
            throw new IllegalStateException("prepare() must run before cases are opened");
        }
        VariantPlan plan = plans.planFor(yamlCase);
        WireMockServices wireMock = registry.wireMock(plan);
        resetBeforeCase(plan, wireMock);
        ContextRegistry.Acquired acquired = acquire(yamlCase, plan);
        if (acquired.started() && !yamlCase.freshContext()) {
            pause.millis(plan.context().settleMs());
            acquired.context().beans().clearInvocations();
            wireMock.resetRequests();
            logs.clear();
        }
        CaseContext caseContext = new CaseContext(acquired.context(), wireMock, yamlCase.freshContext());
        boolean registered = false;
        try {
            registerLayers(yamlCase, plan, caseContext, errors);
            registered = true;
        } finally {
            if (!registered) {
                caseContext.close();
            }
        }
        return caseContext;
    }

    /** Registers a step's stubs on top of the case's, for that step and later ones. */
    public void applyStepStubs(CaseContext caseContext, Step step, StubErrors errors, String where) {
        StubLayers.beanStubs(step.stubs(), where).forEach(stub -> apply(caseContext.running(), stub, errors));
        StubLayers.httpStubs(step.stubs(), where).forEach(stub -> caseContext.wireMock().register(stub.stub(),
                stub.where()));
    }

    @Override
    public void close() {
        registry.close();
    }

    private void resetBeforeCase(VariantPlan plan, WireMockServices wireMock) {
        if (!rootsWithCases.add(plan.key().root())) {
            pause.millis(plan.context().settleMs());
        }
        registry.running(plan.key()).ifPresent(running -> running.beans().resetAll());
        wireMock.resetAll();
        logs.attach(plan.loggers());
        logs.clear();
    }

    private ContextRegistry.Acquired acquire(YamlCase yamlCase, VariantPlan plan) {
        try {
            return registry.acquire(plan, yamlCase.freshContext());
        } catch (IllegalStateException e) {
            throw new IllegalStateException(yamlCase.where() + ": " + e.getMessage(), e);
        }
    }

    private void registerLayers(YamlCase yamlCase, VariantPlan plan, CaseContext caseContext, StubErrors errors) {
        String where = yamlCase.where();
        RootConfiguration root = plan.root();
        RunningContext running = caseContext.running();
        List<StubLayers.BeanStub> defaults = StubLayers.beanStubs(root.defaults(), root.defaultsLabel()).stream()
                .filter(stub -> !plan.defaultsOnly().contains(stub.bean())
                        || running.context().containsBean(stub.bean()))
                .toList();
        List<StubLayers.BeanStub> beanStubs = StubLayers.merge(List.of(
                bootStubs(plan),
                defaults,
                StubLayers.beanStubs(yamlCase.fileDefaults(), where + ", file defaults"),
                StubLayers.beanStubs(yamlCase.stubs(), where)), StubLayers.BeanStub::key);
        beanStubs.forEach(stub -> apply(running, stub, errors));
        List<StubLayers.HttpStubAt> httpStubs = StubLayers.merge(List.of(
                StubLayers.httpStubs(root.defaults(), root.defaultsLabel()),
                StubLayers.httpStubs(yamlCase.fileDefaults(), where + ", file defaults"),
                StubLayers.httpStubs(yamlCase.stubs(), where)), StubLayers.HttpStubAt::key);
        httpStubs.forEach(stub -> caseContext.wireMock().register(stub.stub(), stub.where()));
    }

    /** Boot-time stubs are re-registered for each case, since the reset before it removed them. */
    private static List<StubLayers.BeanStub> bootStubs(VariantPlan plan) {
        String label = plan.context().label();
        List<StubLayers.BeanStub> boot = new ArrayList<>();
        plan.mocks().values().forEach(mock -> mock.stubs().forEach(stub -> boot.add(
                new StubLayers.BeanStub(mock.name(), stub, label + ", mockBeans " + mock.name()))));
        plan.spies().values().forEach(spy -> spy.stubs().forEach(stub -> boot.add(
                new StubLayers.BeanStub(spy.name(), stub, label + ", spyBeans " + spy.name()))));
        return boot;
    }

    private void apply(RunningContext running, StubLayers.BeanStub stub, StubErrors errors) {
        OverriddenBean bean = running.overridden(stub.bean(), stub.where());
        stubs.apply(bean.instance(), bean.declaredType(), List.of(stub.stub()), errors, stub.where());
    }
}
