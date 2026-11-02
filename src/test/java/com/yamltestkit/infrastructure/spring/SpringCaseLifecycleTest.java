package com.yamltestkit.infrastructure.spring;

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.yamltestkit.domain.model.BeanEntry;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.ContextVariant;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.HttpStub;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.StubResponse;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.domain.plan.ContextPlanner;
import com.yamltestkit.domain.plan.VariantKey;
import com.yamltestkit.domain.plan.VariantPlan;
import com.yamltestkit.infrastructure.logback.LogCapture;
import com.yamltestkit.infrastructure.mockito.StubApplier;
import com.yamltestkit.infrastructure.mockito.StubErrors;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringCaseLifecycleTest {

    private static final String ROOT = "cases/";
    private static final MethodRef FIND_ALL = new MethodRef("findAll", null);
    private static final Stub BOOT_STUB = new Stub(FIND_ALL, null, new StubAnswer.Returns(IntNode.valueOf(1)));
    private static final Stub DEFAULTS_STUB = new Stub(new MethodRef("count", null), null,
            new StubAnswer.Returns(IntNode.valueOf(7)));
    private static final Stub CASE_STUB = new Stub(FIND_ALL, null, new StubAnswer.Returns(IntNode.valueOf(3)));
    private static final HttpStub PARTNER_STUB = new HttpStub("partner", new RequestMatcher("GET", "/items", null,
            Map.of(), Map.of(), MissingNode.getInstance()), List.of(new StubResponse(200, Map.of(),
            MissingNode.getInstance(), 0, null, false)), false);
    private static final ContextFile CONTEXT = new ContextFile("cases/_context.yaml", "com.example.App", List.of(),
            new ContextVariant(Map.of(), List.of(new MockBean("feedRepository", null, false, List.of(BOOT_STUB))),
                    List.of(), List.of()), Map.of(), List.of("partner"), List.of(), 25);
    private static final RootConfiguration ROOT_CONFIGURATION = new RootConfiguration(ROOT, CONTEXT,
            new StubSet(List.of(new BeanEntry("reportStore", List.of(DEFAULTS_STUB))), List.of(), List.of()),
            "cases/_caseDefaults.yaml", null);
    private static final CaseSource SOURCE = new CaseSource(URI.create("classpath:/cases/a.yaml"), ROOT, "a.yaml");
    private static final Step STEP = new Step("main", new Call.Bean("feedCache", new MethodRef("size", null),
            List.of()), StubSet.EMPTY, Expect.NONE);
    private static final YamlCase PLAIN_CASE = yamlCase("plain", StubSet.EMPTY, false);
    private static final YamlCase STUBBING_CASE = yamlCase("stubbing", new StubSet(
            List.of(new BeanEntry("feedRepository", List.of(CASE_STUB))), List.of(), List.of(PARTNER_STUB)), false);
    private static final YamlCase FRESH_CASE = yamlCase("fresh", StubSet.EMPTY, true);
    private static final List<CaseFile> FILES = List.of(new CaseFile(SOURCE, List.of(PLAIN_CASE, STUBBING_CASE,
            FRESH_CASE)));
    private static final VariantPlan PLAN = ContextPlanner.plan(Map.of(ROOT, ROOT_CONFIGURATION), FILES)
            .planFor(PLAIN_CASE);
    private static final VariantKey KEY = new VariantKey(ROOT, null);

    private ContextRegistry mockRegistry;
    private StubApplier mockStubs;
    private Pause mockPause;
    private RunningContext running;
    private ConfigurableApplicationContext mockContext;
    private ContextBeans mockBeans;
    private WireMockServices mockWireMock;
    private StubErrors errors;
    private LogCapture mockLogs;
    private Object feedRepository;
    private Object reportStore;

    private SpringCaseLifecycle underTest;

    @BeforeEach
    void setUp() {
        mockRegistry = mock(ContextRegistry.class);
        mockStubs = mock(StubApplier.class);
        mockPause = mock(Pause.class);
        mockContext = mock(ConfigurableApplicationContext.class);
        mockBeans = mock(ContextBeans.class);
        running = new RunningContext(mockContext, null, mockBeans);
        mockWireMock = mock(WireMockServices.class);
        errors = new StubErrors();
        mockLogs = mock(LogCapture.class);
        feedRepository = new Object();
        reportStore = new Object();
        when(mockContext.containsBean("reportStore")).thenReturn(true);
        when(mockBeans.find("feedRepository")).thenReturn(Optional.of(new OverriddenBean(feedRepository,
                Object.class)));
        when(mockBeans.find("reportStore")).thenReturn(Optional.of(new OverriddenBean(reportStore, Object.class)));
        underTest = new SpringCaseLifecycle(mockRegistry, mockStubs, Map.of(ROOT, ROOT_CONFIGURATION), mockLogs,
                mockPause);
        underTest.prepare(FILES);
        when(mockRegistry.wireMock(PLAN)).thenReturn(mockWireMock);
    }

    @Test
    void open_whenThisCaseStartsTheSharedContext_shouldResetBeforeAndForgetStartupCallsAfterwards() {
        when(mockRegistry.running(KEY)).thenReturn(Optional.empty());
        when(mockRegistry.acquire(PLAN, false)).thenReturn(new ContextRegistry.Acquired(running, true));

        CaseContext result = underTest.open(PLAIN_CASE, errors);

        assertThat(result).isEqualTo(new CaseContext(running, mockWireMock, false));
        InOrder order = inOrder(mockWireMock, mockLogs, mockRegistry, mockPause, mockBeans, mockStubs);
        order.verify(mockWireMock).resetAll();
        order.verify(mockLogs).attach(Set.of());
        order.verify(mockLogs).clear();
        order.verify(mockRegistry).acquire(PLAN, false);
        order.verify(mockPause).millis(25);
        order.verify(mockBeans).clearInvocations();
        order.verify(mockWireMock).resetRequests();
        order.verify(mockLogs).clear();
        order.verify(mockStubs).apply(feedRepository, Object.class, List.of(BOOT_STUB), errors,
                "cases/_context.yaml, mockBeans feedRepository");
        order.verify(mockStubs).apply(reportStore, Object.class, List.of(DEFAULTS_STUB), errors,
                "cases/_caseDefaults.yaml, mocks reportStore");
        verify(mockBeans, never()).resetAll();
        verify(mockPause, times(1)).millis(25);
    }

    @Test
    void open_whenTheSharedContextRuns_shouldResetItsMocksBeforeAcquiringAndKeepTheirInvocations() {
        when(mockRegistry.running(KEY)).thenReturn(Optional.of(running));
        when(mockRegistry.acquire(PLAN, false)).thenReturn(new ContextRegistry.Acquired(running, false));

        underTest.open(PLAIN_CASE, errors);

        InOrder order = inOrder(mockBeans, mockWireMock, mockLogs, mockRegistry);
        order.verify(mockBeans).resetAll();
        order.verify(mockWireMock).resetAll();
        order.verify(mockLogs).clear();
        order.verify(mockRegistry).acquire(PLAN, false);
        verify(mockLogs, times(1)).clear();
        verify(mockBeans, never()).clearInvocations();
        verify(mockWireMock, never()).resetRequests();
    }

    @Test
    void open_whenTheCaseWantsAFreshContext_shouldKeepWhatItsStartupRecorded() {
        when(mockRegistry.running(KEY)).thenReturn(Optional.empty());
        when(mockRegistry.acquire(PLAN, true)).thenReturn(new ContextRegistry.Acquired(running, true));

        CaseContext result = underTest.open(FRESH_CASE, errors);

        assertThat(result.fresh()).isTrue();
        verify(mockPause, never()).millis(25);
        verify(mockBeans, never()).clearInvocations();
        verify(mockWireMock, never()).resetRequests();
        verify(mockLogs, times(1)).clear();
    }

    @Test
    void open_whenTheCaseStubsWhatABootTimeStubAlsoStubs_shouldRegisterOnlyTheCaseStub() {
        when(mockRegistry.running(KEY)).thenReturn(Optional.empty());
        when(mockRegistry.acquire(PLAN, false)).thenReturn(new ContextRegistry.Acquired(running, true));

        underTest.open(STUBBING_CASE, errors);

        verify(mockStubs).apply(feedRepository, Object.class, List.of(CASE_STUB), errors,
                "cases/a.yaml, case 'stubbing', mocks feedRepository");
        verify(mockStubs, never()).apply(feedRepository, Object.class, List.of(BOOT_STUB), errors,
                "cases/_context.yaml, mockBeans feedRepository");
        verify(mockWireMock).register(PARTNER_STUB, "cases/a.yaml, case 'stubbing', http partner");
        verify(mockContext).getBean("feedRepository");
    }

    @Test
    void open_whenTheRootAlreadyRanACase_shouldWaitSettleMsBeforeResetting() {
        when(mockRegistry.running(KEY)).thenReturn(Optional.of(running));
        when(mockRegistry.acquire(PLAN, false)).thenReturn(new ContextRegistry.Acquired(running, false));
        underTest.open(PLAIN_CASE, errors);

        underTest.open(PLAIN_CASE, errors);

        InOrder order = inOrder(mockBeans, mockPause);
        order.verify(mockBeans).resetAll();
        order.verify(mockPause).millis(25);
        order.verify(mockBeans).resetAll();
        verify(mockPause, times(1)).millis(25);
    }

    @Test
    void open_whenTheContextFailsToStart_shouldFailNamingTheCase() {
        IllegalStateException startupFailure = new IllegalStateException("cases/ variant 'default': the application"
                + " context failed to start: boom");
        when(mockRegistry.running(KEY)).thenReturn(Optional.empty());
        when(mockRegistry.acquire(PLAN, false)).thenThrow(startupFailure);

        assertThatThrownBy(() -> underTest.open(PLAIN_CASE, errors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cases/a.yaml, case 'plain': cases/ variant 'default': the application context failed to"
                        + " start: boom")
                .hasCause(startupFailure);
    }

    @Test
    void open_whenRegisteringAFreshContextsStubsFails_shouldCloseTheFreshContext() {
        IllegalStateException stubFailure = new IllegalStateException("no method 'findAll'");
        when(mockRegistry.running(KEY)).thenReturn(Optional.empty());
        when(mockRegistry.acquire(PLAN, true)).thenReturn(new ContextRegistry.Acquired(running, true));
        doThrow(stubFailure).when(mockStubs).apply(feedRepository, Object.class, List.of(BOOT_STUB), errors,
                "cases/_context.yaml, mockBeans feedRepository");

        assertThatThrownBy(() -> underTest.open(FRESH_CASE, errors)).isSameAs(stubFailure);

        verify(mockContext).close();
    }

    @Test
    void open_whenRegisteringASharedContextsStubsFails_shouldKeepTheSharedContextOpen() {
        IllegalStateException stubFailure = new IllegalStateException("no method 'findAll'");
        when(mockRegistry.running(KEY)).thenReturn(Optional.of(running));
        when(mockRegistry.acquire(PLAN, false)).thenReturn(new ContextRegistry.Acquired(running, false));
        doThrow(stubFailure).when(mockStubs).apply(feedRepository, Object.class, List.of(BOOT_STUB), errors,
                "cases/_context.yaml, mockBeans feedRepository");

        assertThatThrownBy(() -> underTest.open(PLAIN_CASE, errors)).isSameAs(stubFailure);

        verify(mockContext, never()).close();
    }

    @Test
    void open_whenADefaultsOnlyBeanIsMissingFromTheContext_shouldSkipItsDefaultsStubs() {
        when(mockContext.containsBean("reportStore")).thenReturn(false);
        when(mockRegistry.running(KEY)).thenReturn(Optional.of(running));
        when(mockRegistry.acquire(PLAN, false)).thenReturn(new ContextRegistry.Acquired(running, false));

        underTest.open(PLAIN_CASE, errors);

        verify(mockStubs).apply(feedRepository, Object.class, List.of(BOOT_STUB), errors,
                "cases/_context.yaml, mockBeans feedRepository");
        verify(mockContext, never()).getBean("reportStore");
        verify(mockBeans, never()).find("reportStore");
    }

    @Test
    void open_whenNoPlanWasPrepared_shouldFail() {
        SpringCaseLifecycle unprepared = new SpringCaseLifecycle(mockRegistry, mockStubs,
                Map.of(ROOT, ROOT_CONFIGURATION), mockLogs, mockPause);

        assertThatThrownBy(() -> unprepared.open(PLAIN_CASE, errors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("prepare() must run before cases are opened");
    }

    @Test
    void applyStepStubs_whenTheStepHasStubs_shouldRegisterThemOnTopOfTheCase() {
        Step step = new Step("reload", STEP.call(), new StubSet(List.of(new BeanEntry("feedRepository",
                List.of(CASE_STUB))), List.of(), List.of(PARTNER_STUB)), Expect.NONE);
        CaseContext caseContext = new CaseContext(running, mockWireMock, false);

        underTest.applyStepStubs(caseContext, step, errors, "cases/a.yaml, case 'c', step 'reload'");

        verify(mockStubs).apply(feedRepository, Object.class, List.of(CASE_STUB), errors,
                "cases/a.yaml, case 'c', step 'reload', mocks feedRepository");
        verify(mockWireMock).register(PARTNER_STUB, "cases/a.yaml, case 'c', step 'reload', http partner");
    }

    private static YamlCase yamlCase(String name, StubSet stubs, boolean fresh) {
        return new YamlCase(name, SOURCE, List.of(STEP), StubSet.EMPTY, stubs, null, fresh);
    }
}
