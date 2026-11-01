package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.yamltestkit.domain.assertion.CheckResult;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CallCount;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.ContextExpectation;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.LogExpectation;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.VerifyCheck;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.infrastructure.logback.LogCapture;
import com.yamltestkit.infrastructure.mockito.MockVerifier;
import com.yamltestkit.infrastructure.spring.CaseContext;
import com.yamltestkit.infrastructure.spring.ContextAssertion;
import com.yamltestkit.infrastructure.spring.OverriddenBean;
import com.yamltestkit.infrastructure.spring.RunningContext;
import com.yamltestkit.infrastructure.wiremock.JournalVerifier;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EffectChecksTest {

    private static final CaseSource SOURCE = new CaseSource(URI.create("classpath:/cases/a.yaml"), "cases/", "a.yaml");
    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";
    private static final String BEAN_WHERE = WHERE + ": verify[0] bean 'auditService' label";
    private static final JsonNode CAPTURED = json("[{\"args\": [\"x\"]}]");
    private static final Call BEAN_CALL = new Call.Bean("feedCache", new MethodRef("size", null), List.of());
    private static final VerifyCheck.OnMock AUDIT_LABEL = onMock(VerifyCheck.MockTarget.BEAN, "auditService", null,
            "labels");
    private static final AssertionFailedError FAILURE = new AssertionFailedError(BEAN_WHERE + ": expected times: 1,"
            + " actual: 0; calls of the mock: none");

    private MockVerifier mockMocks;
    private JournalVerifier mockJournals;
    private LogCapture mockLogs;
    private ContextAssertion mockContexts;
    private RunningContext mockRunning;
    private ConfigurableApplicationContext mockContext;
    private WireMockServices mockWireMock;
    private Object auditService;

    private EffectChecks underTest;

    @BeforeEach
    void setUp() {
        mockMocks = mock(MockVerifier.class);
        mockJournals = mock(JournalVerifier.class);
        mockLogs = mock(LogCapture.class);
        mockContexts = mock(ContextAssertion.class);
        mockRunning = mock(RunningContext.class);
        mockContext = mock(ConfigurableApplicationContext.class);
        mockWireMock = mock(WireMockServices.class);
        auditService = new Object();
        when(mockRunning.context()).thenReturn(mockContext);
        when(mockRunning.overridden("auditService", BEAN_WHERE)).thenReturn(new OverriddenBean(auditService,
                Object.class));
        underTest = new EffectChecks(mockMocks, mockJournals, mockLogs, mockContexts);
    }

    @Test
    void check_whenABeanCheckCaptures_shouldVerifyTheBeansMockAndKeepTheCaptureForReferences() {
        CaseRun run = springRun(expect(List.of(AUDIT_LABEL), List.of(), null));
        when(mockMocks.verify(AUDIT_LABEL, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE))
                .thenReturn(new CheckResult(List.of(), CAPTURED));

        List<AssertionFailedError> result = underTest.check(run, run.yamlCase().steps().getFirst());

        assertThat(result).isEmpty();
        assertThat(run.resolve(JsonNodeFactory.instance.objectNode().put("ref", "labels"))).isEqualTo(CAPTURED);
    }

    @Test
    void check_whenADepCheckRuns_shouldVerifyTheStepsDependencyMockAgainstItsMockedType() {
        Runnable mockClock = mock(Runnable.class);
        VerifyCheck.OnMock check = onMock(VerifyCheck.MockTarget.DEP, "clock", new CallCount.Times(1), null);
        CaseRun run = unitRun(expect(List.of(check), List.of(), null));
        Step step = run.yamlCase().steps().getFirst();
        run.rememberUnitMocks(step, Map.of("clock", mockClock));
        String where = WHERE + ": verify[0] dep 'clock' label";
        when(mockMocks.verify(check, mockClock, Runnable.class, run, run.stubErrors(), where))
                .thenReturn(new CheckResult(List.of(FAILURE), null));

        List<AssertionFailedError> result = underTest.check(run, step);

        assertThat(result).containsExactly(FAILURE);
    }

    @Test
    void check_whenADepCheckNamesNoMockOfTheInstance_shouldFailListingItsMocks() {
        VerifyCheck.OnMock check = onMock(VerifyCheck.MockTarget.DEP, "limits", new CallCount.Times(1), null);
        CaseRun run = unitRun(expect(List.of(check), List.of(), null));
        Step step = run.yamlCase().steps().getFirst();
        run.rememberUnitMocks(step, Map.of("ctaIds", new Object(), "clock", new Object()));

        assertThatThrownBy(() -> underTest.check(run, step))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": verify[0] dep 'limits' label: the instance has no mock dependency 'limits';"
                        + " its mocks (by deps key or parameter name): [clock, ctaIds]");
    }

    @Test
    void check_whenAServiceCheckRuns_shouldVerifyTheJournalOfTheCasesWireMock() {
        VerifyCheck.OnService check = new VerifyCheck.OnService("partner", new RequestMatcher("POST", "/bid", null,
                Map.of(), Map.of(), MissingNode.getInstance()), new CallCount.Times(1), null, null, List.of());
        CaseRun run = springRun(expect(List.of(check), List.of(), null));
        when(mockJournals.verify(check, mockWireMock, run, WHERE + ": verify[0] service 'partner'"))
                .thenReturn(new CheckResult(List.of(FAILURE), null));

        List<AssertionFailedError> result = underTest.check(run, run.yamlCase().steps().getFirst());

        assertThat(result).containsExactly(FAILURE);
    }

    @Test
    void check_whenACheckWithWithinPassesOnALaterAttempt_shouldPollUntilItHolds() {
        VerifyCheck.OnMock check = withinCheck(Duration.ofSeconds(10));
        CaseRun run = springRun(expect(List.of(check), List.of(), null));
        when(mockMocks.verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE))
                .thenReturn(new CheckResult(List.of(FAILURE), null), new CheckResult(List.of(), null));

        List<AssertionFailedError> result = underTest.check(run, run.yamlCase().steps().getFirst());

        assertThat(result).isEmpty();
        verify(mockMocks, times(2)).verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE);
    }

    @Test
    void check_whenACheckWithWithinKeepsFailing_shouldReportTheLastFailureWithTheWait() {
        VerifyCheck.OnMock check = withinCheck(Duration.ofMillis(150));
        CaseRun run = springRun(expect(List.of(check), List.of(), null));
        when(mockMocks.verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE))
                .thenReturn(new CheckResult(List.of(FAILURE), null));

        List<AssertionFailedError> result = underTest.check(run, run.yamlCase().steps().getFirst());

        assertThat(result).singleElement().satisfies(failure -> assertThat(failure)
                .hasMessage(FAILURE.getMessage() + " (still failing after waiting 150 ms, within)")
                .hasCause(FAILURE));
    }

    @Test
    void check_whenANeverCheckWithWithinStaysAtZero_shouldKeepCheckingForTheWholeDuration() {
        VerifyCheck.OnMock check = withinCheck(new CallCount.Times(0), Duration.ofMillis(250));
        CaseRun run = springRun(expect(List.of(check), List.of(), null));
        when(mockMocks.verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE))
                .thenReturn(new CheckResult(List.of(), null));

        List<AssertionFailedError> result = underTest.check(run, run.yamlCase().steps().getFirst());

        assertThat(result).isEmpty();
        verify(mockMocks, atLeast(3)).verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE);
    }

    @Test
    void check_whenACallArrivesDuringTheWithinOfANeverCheck_shouldFailAtOnceWithTheWait() {
        VerifyCheck.OnMock check = withinCheck(new CallCount.Times(0), Duration.ofSeconds(10));
        CaseRun run = springRun(expect(List.of(check), List.of(), null));
        when(mockMocks.verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE))
                .thenReturn(new CheckResult(List.of(), null), new CheckResult(List.of(FAILURE), null));

        List<AssertionFailedError> result = underTest.check(run, run.yamlCase().steps().getFirst());

        assertThat(result).singleElement().satisfies(failure -> assertThat(failure)
                .hasMessage(FAILURE.getMessage() + " (a call arrived while waiting 10000 ms, within)")
                .hasCause(FAILURE));
        verify(mockMocks, times(2)).verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE);
    }

    @Test
    void check_whenANoInteractionsCheckWithWithinStaysAtZero_shouldKeepCheckingForTheWholeDuration() {
        VerifyCheck.OnMock check = withinCheck(new CallCount.NoInteractions(), Duration.ofMillis(250));
        CaseRun run = springRun(expect(List.of(check), List.of(), null));
        when(mockMocks.verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE))
                .thenReturn(new CheckResult(List.of(), null));

        List<AssertionFailedError> result = underTest.check(run, run.yamlCase().steps().getFirst());

        assertThat(result).isEmpty();
        verify(mockMocks, atLeast(3)).verify(check, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE);
    }

    @Test
    void check_whenTheStepExpectsLogsAndContext_shouldReportTheirFailuresAfterTheVerifications() {
        LogExpectation log = new LogExpectation("com.example.FeedLoader", LogExpectation.LogLevel.WARN, null, null,
                false);
        ContextExpectation context = new ContextExpectation(new ContextExpectation.Presence(List.of("feedCache"),
                List.of()), ContextExpectation.Presence.NONE);
        AssertionFailedError logFailure = new AssertionFailedError(WHERE + ": logs: expected a WARN event");
        AssertionFailedError contextFailure = new AssertionFailedError(WHERE + ": context: expected bean");
        CaseRun run = springRun(expect(List.of(AUDIT_LABEL), List.of(log), context));
        when(mockMocks.verify(AUDIT_LABEL, auditService, Object.class, run, run.stubErrors(), BEAN_WHERE))
                .thenReturn(new CheckResult(List.of(FAILURE), null));
        when(mockLogs.check(List.of(log), WHERE)).thenReturn(List.of(logFailure));
        when(mockContexts.check(context, mockContext, WHERE)).thenReturn(List.of(contextFailure));

        List<AssertionFailedError> result = underTest.check(run, run.yamlCase().steps().getFirst());

        assertThat(result).containsExactly(FAILURE, logFailure, contextFailure);
    }

    private CaseRun springRun(Expect expect) {
        CaseRun run = run(BEAN_CALL, expect);
        run.attach(new CaseContext(mockRunning, mockWireMock, false));
        return run;
    }

    private static CaseRun unitRun(Expect expect) {
        return run(new Call.Unit("com.example.Unit", new MethodRef("run", null), List.of(), null, Map.of()), expect);
    }

    private static CaseRun run(Call call, Expect expect) {
        Step step = new Step(Step.IMPLICIT_NAME, call, StubSet.EMPTY, expect);
        return new CaseRun(new YamlCase("c", SOURCE, List.of(step), StubSet.EMPTY, StubSet.EMPTY, null, false));
    }

    private static VerifyCheck.OnMock withinCheck(Duration within) {
        return withinCheck(new CallCount.Times(1), within);
    }

    private static VerifyCheck.OnMock withinCheck(CallCount count, Duration within) {
        return new VerifyCheck.OnMock(VerifyCheck.MockTarget.BEAN, "auditService", new MethodRef("label", null), null,
                null, count, within, null, List.of());
    }

    private static VerifyCheck.OnMock onMock(VerifyCheck.MockTarget target, String name, CallCount count, String as) {
        return new VerifyCheck.OnMock(target, name, new MethodRef("label", null), null, null,
                count == null ? CallCount.AT_LEAST_ONCE : count, null, as, List.of());
    }

    private static Expect expect(List<VerifyCheck> verify, List<LogExpectation> logs, ContextExpectation context) {
        return new Expect(null, Map.of(), null, null, List.of(), MissingNode.getInstance(), null, verify, logs,
                context, null);
    }
}
