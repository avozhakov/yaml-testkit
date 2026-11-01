package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.yamltestkit.domain.model.BrokenCase;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.LogExpectation;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Polling;
import com.yamltestkit.domain.model.Query;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.infrastructure.logback.LogCapture;
import com.yamltestkit.infrastructure.mockito.StubErrors;
import com.yamltestkit.infrastructure.spring.CaseContext;
import com.yamltestkit.infrastructure.spring.SpringCaseLifecycle;
import com.yamltestkit.infrastructure.yaml.CaseFileLoader;
import com.yamltestkit.infrastructure.yaml.CaseResource;
import com.yamltestkit.infrastructure.yaml.CaseResourceLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class YamlCaseEngineTest {

    private static final List<String> GLOBS = List.of("cases/**");
    private static final CaseSource ROOT_FILE = source("root.yaml");
    private static final CaseSource DISCOVER_FILE = source("discover/ebay-off.yaml");
    private static final Step HTTP_STEP = step("call-is", new Call.Http("GET", "/is", Query.NONE, Map.of(),
            MissingNode.getInstance()));
    private static final Step BEAN_STEP = step("reload", new Call.Bean("feedCache", new MethodRef("reload", null),
            List.of()));
    private static final Step UNIT_STEP = step("main", new Call.Unit("com.example.Unit", new MethodRef("run", null),
            List.of(), null, Map.of()));
    private static final YamlCase HTTP_CASE = yamlCase("http case", ROOT_FILE, HTTP_STEP);
    private static final YamlCase UNIT_CASE = yamlCase("unit case", ROOT_FILE, UNIT_STEP);
    private static final YamlCase MIXED_CASE = yamlCase("mixed case", DISCOVER_FILE, HTTP_STEP, UNIT_STEP);
    private static final YamlCase BEAN_CASE = yamlCase("bean case", DISCOVER_FILE, BEAN_STEP, HTTP_STEP);
    private static final BrokenCase BROKEN_CASE = new BrokenCase("broken", DISCOVER_FILE, "ebay-off.yaml: broken");
    private static final CaseFile ROOT_CASES = new CaseFile(ROOT_FILE, List.of(HTTP_CASE, UNIT_CASE));
    private static final CaseFile DISCOVER_CASES = new CaseFile(DISCOVER_FILE,
            List.of(MIXED_CASE, BEAN_CASE, BROKEN_CASE));

    private CaseResourceLocator mockLocator;
    private CaseFileLoader mockLoader;
    private StepExecutor mockHttpExecutor;
    private StepExecutor mockBeanExecutor;
    private StepExecutor mockUnitExecutor;
    private SpringCaseLifecycle mockSpringCases;
    private CaseContext mockCaseContext;
    private LogCapture mockLogs;

    @BeforeEach
    void setUp() {
        mockLocator = mock(CaseResourceLocator.class);
        mockLoader = mock(CaseFileLoader.class);
        mockHttpExecutor = mock(StepExecutor.class);
        mockBeanExecutor = mock(StepExecutor.class);
        mockUnitExecutor = mock(StepExecutor.class);
        mockSpringCases = mock(SpringCaseLifecycle.class);
        mockCaseContext = mock(CaseContext.class);
        mockLogs = mock(LogCapture.class);
    }

    @Test
    void load_whenGlobsMatchFiles_shouldLoadEachFileInLocatorOrder() {
        CaseResource first = new CaseResource(new ClassPathResource("cases/root.yaml"), ROOT_FILE);
        CaseResource second = new CaseResource(new ClassPathResource("cases/discover/ebay-off.yaml"), DISCOVER_FILE);
        when(mockLocator.locate(GLOBS)).thenReturn(List.of(first, second));
        when(mockLoader.load(first)).thenReturn(ROOT_CASES);
        when(mockLoader.load(second)).thenReturn(DISCOVER_CASES);

        List<CaseFile> result = engine(EnumSet.allOf(CallKind.class), null).load();

        assertThat(result).containsExactly(ROOT_CASES, DISCOVER_CASES);
    }

    @Test
    void plan_whenAllKindsAreSelected_shouldKeepEveryCaseInFileOrder() {
        CasePlan result = engine(EnumSet.allOf(CallKind.class), null).plan(List.of(ROOT_CASES, DISCOVER_CASES));

        assertThat(result.files()).containsExactly(ROOT_CASES, DISCOVER_CASES);
    }

    @Test
    void plan_whenFilterSelectsSomeCases_shouldPrepareTheContextsFromAllLoadedFiles() {
        engine(EnumSet.of(CallKind.CLASS), "discover/**").plan(List.of(ROOT_CASES, DISCOVER_CASES));

        verify(mockSpringCases).prepare(List.of(ROOT_CASES, DISCOVER_CASES));
    }

    @Test
    void plan_whenKindsAreRestricted_shouldKeepCasesWhoseCallsAreAllOfThoseKindsAndBrokenCases() {
        CasePlan result = engine(EnumSet.of(CallKind.HTTP, CallKind.BEAN), null)
                .plan(List.of(ROOT_CASES, DISCOVER_CASES));

        assertThat(result.files()).containsExactly(
                new CaseFile(ROOT_FILE, List.of(HTTP_CASE)),
                new CaseFile(DISCOVER_FILE, List.of(BEAN_CASE, BROKEN_CASE)));
    }

    @Test
    void plan_whenNoCaseOfAFileIsSelected_shouldDropTheFile() {
        CasePlan result = engine(EnumSet.of(CallKind.CLASS), null).plan(List.of(ROOT_CASES, DISCOVER_CASES));

        assertThat(result.files()).containsExactly(
                new CaseFile(ROOT_FILE, List.of(UNIT_CASE)),
                new CaseFile(DISCOVER_FILE, List.of(BROKEN_CASE)));
    }

    @Test
    void plan_whenFilterSelectsAFolder_shouldKeepOnlyCasesBelowIt() {
        CasePlan result = engine(EnumSet.allOf(CallKind.class), "discover/**")
                .plan(List.of(ROOT_CASES, DISCOVER_CASES));

        assertThat(result.files()).containsExactly(DISCOVER_CASES);
    }

    @Test
    void plan_whenFilterNamesOneCase_shouldKeepOnlyThatCase() {
        CasePlan result = engine(EnumSet.allOf(CallKind.class), "*.yaml/unit*")
                .plan(List.of(ROOT_CASES, DISCOVER_CASES));

        assertThat(result.files()).containsExactly(new CaseFile(ROOT_FILE, List.of(UNIT_CASE)));
    }

    @Test
    void run_whenStepsPass_shouldOpenTheContextAndExecuteEachStepAfterItsStubsInOrder() throws Exception {
        ArgumentCaptor<StubErrors> openedWith = ArgumentCaptor.forClass(StubErrors.class);
        when(mockSpringCases.open(eq(BEAN_CASE), openedWith.capture())).thenReturn(mockCaseContext);
        ArgumentCaptor<CaseRun> runCaptor = ArgumentCaptor.forClass(CaseRun.class);

        CaseResult result = engine(EnumSet.allOf(CallKind.class), null).run(BEAN_CASE);

        assertThat(result).isEqualTo(CaseResult.passed("discover/ebay-off.yaml/bean case"));
        verify(mockBeanExecutor).execute(runCaptor.capture(), eq(BEAN_STEP));
        CaseRun run = runCaptor.getValue();
        StubErrors errors = openedWith.getValue();
        assertThat(run.yamlCase()).isEqualTo(BEAN_CASE);
        assertThat(run.caseContext()).isSameAs(mockCaseContext);
        assertThat(errors).isSameAs(run.stubErrors());
        InOrder order = inOrder(mockSpringCases, mockBeanExecutor, mockHttpExecutor, mockCaseContext);
        order.verify(mockSpringCases).open(BEAN_CASE, errors);
        order.verify(mockSpringCases).applyStepStubs(mockCaseContext, BEAN_STEP, errors,
                "cases/discover/ebay-off.yaml, case 'bean case', step 'reload'");
        order.verify(mockBeanExecutor).execute(run, BEAN_STEP);
        order.verify(mockSpringCases).applyStepStubs(mockCaseContext, HTTP_STEP, errors,
                "cases/discover/ebay-off.yaml, case 'bean case', step 'call-is'");
        order.verify(mockHttpExecutor).execute(run, HTTP_STEP);
        order.verify(mockCaseContext).close();
        verify(mockUnitExecutor, never()).execute(any(), any());
    }

    @Test
    void run_whenCaseHasOnlyClassCalls_shouldNotOpenAContext() throws Exception {
        ArgumentCaptor<CaseRun> runCaptor = ArgumentCaptor.forClass(CaseRun.class);

        CaseResult result = engine(EnumSet.allOf(CallKind.class), null).run(UNIT_CASE);

        assertThat(result.isPassed()).isTrue();
        verify(mockUnitExecutor).execute(runCaptor.capture(), eq(UNIT_STEP));
        assertThat(runCaptor.getValue().yamlCase()).isEqualTo(UNIT_CASE);
        verify(mockSpringCases, never()).open(any(), any());
    }

    @Test
    void run_whenAStepFails_shouldReturnTheFailureSkipLaterStepsAndCloseTheContext() throws Exception {
        ArgumentCaptor<StubErrors> openedWith = ArgumentCaptor.forClass(StubErrors.class);
        ArgumentCaptor<CaseRun> runCaptor = ArgumentCaptor.forClass(CaseRun.class);
        when(mockSpringCases.open(eq(MIXED_CASE), openedWith.capture())).thenReturn(mockCaseContext);
        AssertionError failure = new AssertionError("status 500, expected 200");
        doThrow(failure).when(mockHttpExecutor).execute(runCaptor.capture(), eq(HTTP_STEP));

        CaseResult result = engine(EnumSet.allOf(CallKind.class), null).run(MIXED_CASE);

        assertThat(result).isEqualTo(CaseResult.failed("discover/ebay-off.yaml/mixed case", failure));
        assertThat(result.isPassed()).isFalse();
        assertThat(runCaptor.getValue().yamlCase()).isEqualTo(MIXED_CASE);
        assertThat(openedWith.getValue()).isSameAs(runCaptor.getValue().stubErrors());
        verify(mockUnitExecutor, never()).execute(any(), any());
        verify(mockCaseContext).close();
    }

    @Test
    void run_whenTheContextCantBeOpened_shouldFailWithoutExecutingAnyStep() throws Exception {
        ArgumentCaptor<StubErrors> openedWith = ArgumentCaptor.forClass(StubErrors.class);
        IllegalStateException startupFailure = new IllegalStateException("cases/ variant 'default' failed to start");
        when(mockSpringCases.open(eq(HTTP_CASE), openedWith.capture())).thenThrow(startupFailure);

        CaseResult result = engine(EnumSet.allOf(CallKind.class), null).run(HTTP_CASE);

        assertThat(result.failure()).isSameAs(startupFailure);
        assertThat(openedWith.getValue()).isNotNull();
        verify(mockHttpExecutor, never()).execute(any(), any());
    }

    @Test
    void run_whenAPollingStepPassesOnALaterAttempt_shouldRerunTheStepUntilItPasses() throws Exception {
        YamlCase pollingCase = yamlCase("polling case", ROOT_FILE, new Step("main", UNIT_STEP.call(), StubSet.EMPTY,
                polling(Duration.ofSeconds(10), Duration.ofMillis(1))));
        ArgumentCaptor<CaseRun> runCaptor = ArgumentCaptor.forClass(CaseRun.class);
        doThrow(new AssertionError("returns 1, expected 3")).doNothing()
                .when(mockUnitExecutor).execute(runCaptor.capture(), eq(pollingCase.steps().getFirst()));

        CaseResult result = engine(EnumSet.allOf(CallKind.class), null).run(pollingCase);

        assertThat(result.isPassed()).isTrue();
        assertThat(runCaptor.getAllValues()).hasSize(2).allSatisfy(run -> assertThat(run.yamlCase())
                .isEqualTo(pollingCase));
    }

    @Test
    void run_whenAClassCaseExpectsLogs_shouldClearTheCaptureAndAttachItsLoggersBeforeTheSteps() throws Exception {
        Expect logging = new Expect(null, Map.of(), null, null, List.of(), MissingNode.getInstance(), null,
                List.of(), List.of(new LogExpectation("com.example.FeedLoader", LogExpectation.LogLevel.WARN, "feed 42",
                null, false)), null, null);
        YamlCase loggingCase = yamlCase("logging case", ROOT_FILE, new Step("main", UNIT_STEP.call(), StubSet.EMPTY,
                logging));

        ArgumentCaptor<CaseRun> runCaptor = ArgumentCaptor.forClass(CaseRun.class);

        CaseResult result = engine(EnumSet.allOf(CallKind.class), null).run(loggingCase);

        assertThat(result.isPassed()).isTrue();
        InOrder order = inOrder(mockLogs, mockUnitExecutor);
        order.verify(mockLogs).clear();
        order.verify(mockLogs).attach(List.of("com.example.FeedLoader"));
        order.verify(mockUnitExecutor).execute(runCaptor.capture(), eq(loggingCase.steps().getFirst()));
        assertThat(runCaptor.getValue().yamlCase()).isEqualTo(loggingCase);
    }

    @Test
    void run_whenTheCaseHasAContext_shouldLeaveLogCaptureToTheContextLifecycle() {
        ArgumentCaptor<StubErrors> openedWith = ArgumentCaptor.forClass(StubErrors.class);
        when(mockSpringCases.open(eq(HTTP_CASE), openedWith.capture())).thenReturn(mockCaseContext);

        engine(EnumSet.allOf(CallKind.class), null).run(HTTP_CASE);

        assertThat(openedWith.getValue()).isNotNull();
        verifyNoInteractions(mockLogs);
    }

    @Test
    void close_whenCalled_shouldCloseTheContextsTheLogCaptureAndTheExecutors() {
        engine(EnumSet.allOf(CallKind.class), null).close();

        verify(mockSpringCases).close();
        verify(mockLogs).close();
        verify(mockHttpExecutor).close();
        verify(mockBeanExecutor).close();
        verify(mockUnitExecutor).close();
    }

    @Test
    void close_whenClosingTheContextsFails_shouldStillCloseTheLogCaptureAndTheExecutors() {
        IllegalStateException failure = new IllegalStateException("closing the YAML Testkit contexts failed");
        doThrow(failure).when(mockSpringCases).close();

        assertThatThrownBy(() -> engine(EnumSet.allOf(CallKind.class), null).close()).isSameAs(failure);

        verify(mockLogs).close();
        verify(mockHttpExecutor).close();
    }

    @Test
    void run_whenCaseIsBroken_shouldFailWithItsReason() {
        CaseResult result = engine(EnumSet.allOf(CallKind.class), null).run(BROKEN_CASE);

        assertThat(result.failure())
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage("ebay-off.yaml: broken");
    }

    private YamlCaseEngine engine(Set<CallKind> kinds, String filter) {
        return new YamlCaseEngine(new SuiteSettings(GLOBS, kinds, List.of(), filter), mockLocator, mockLoader,
                Map.of(CallKind.HTTP, mockHttpExecutor, CallKind.BEAN, mockBeanExecutor,
                        CallKind.CLASS, mockUnitExecutor), mockSpringCases, mockLogs);
    }

    private static Expect polling(Duration within, Duration every) {
        return new Expect(null, Map.of(), null, null, List.of(), MissingNode.getInstance(), null, List.of(),
                List.of(), null, new Polling(within, every));
    }

    private static CaseSource source(String relativePath) {
        return new CaseSource(URI.create("classpath:/cases/" + relativePath), "cases/", relativePath);
    }

    private static Step step(String name, Call call) {
        return new Step(name, call, StubSet.EMPTY, Expect.NONE);
    }

    private static YamlCase yamlCase(String name, CaseSource source, Step... steps) {
        return new YamlCase(name, source, List.of(steps), StubSet.EMPTY, StubSet.EMPTY, null, false);
    }
}
