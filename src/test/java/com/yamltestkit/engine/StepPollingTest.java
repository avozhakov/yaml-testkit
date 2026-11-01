package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Polling;
import com.yamltestkit.domain.model.Query;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.infrastructure.spring.CaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StepPollingTest {

    private static final CaseSource SOURCE = new CaseSource(URI.create("classpath:/cases/root.yaml"), "cases/",
            "root.yaml");
    private static final Call UNIT_CALL = new Call.Unit("com.example.Unit", new MethodRef("run", null), List.of(),
            null, Map.of());
    private static final Call HTTP_CALL = new Call.Http("GET", "/is", Query.NONE, Map.of(), MissingNode.getInstance());

    private StepExecutor mockExecutor;
    private CaseContext mockCaseContext;

    @BeforeEach
    void setUp() {
        mockExecutor = mock(StepExecutor.class);
        mockCaseContext = mock(CaseContext.class);
    }

    @Test
    void execute_whenTheStepDoesNotPoll_shouldRunItOnce() throws Exception {
        Step step = new Step("main", UNIT_CALL, StubSet.EMPTY, Expect.NONE);
        CaseRun run = run(step);

        StepPolling.execute(mockExecutor, run, step);

        verify(mockExecutor).execute(run, step);
    }

    @Test
    void execute_whenAPollingStepPassesOnALaterAttempt_shouldRerunTheStepUntilItPasses() throws Exception {
        Step step = pollingStep(UNIT_CALL, Duration.ofSeconds(10), Duration.ofMillis(1));
        CaseRun run = run(step);
        doThrow(new AssertionError("returns 1, expected 3"), new AssertionError("returns 2, expected 3"))
                .doNothing()
                .when(mockExecutor).execute(run, step);

        StepPolling.execute(mockExecutor, run, step);

        verify(mockExecutor, times(3)).execute(run, step);
    }

    @Test
    void execute_whenAPollingStepIsRetried_shouldClearTheRecordedCallsBeforeEachRetryOnly() throws Exception {
        Step step = pollingStep(HTTP_CALL, Duration.ofSeconds(10), Duration.ofMillis(1));
        CaseRun run = run(step);
        run.attach(mockCaseContext);
        doThrow(new AssertionError("expected times: 1, actual: 2")).doNothing().when(mockExecutor).execute(run, step);

        StepPolling.execute(mockExecutor, run, step);

        InOrder order = inOrder(mockExecutor, mockCaseContext);
        order.verify(mockExecutor).execute(run, step);
        order.verify(mockCaseContext).clearRecordings();
        order.verify(mockExecutor).execute(run, step);
        verify(mockCaseContext, times(1)).clearRecordings();
    }

    @Test
    void execute_whenAPollingStepPassesAtOnce_shouldNotClearRecordedCalls() throws Exception {
        Step step = pollingStep(HTTP_CALL, Duration.ofSeconds(10), Duration.ofMillis(1));
        CaseRun run = run(step);
        run.attach(mockCaseContext);

        StepPolling.execute(mockExecutor, run, step);

        verify(mockCaseContext, never()).clearRecordings();
    }

    @Test
    void execute_whenAPollingStepKeepsFailing_shouldReportTheLastFailureAfterTheDuration() throws Exception {
        Step step = pollingStep(UNIT_CALL, Duration.ofMillis(30), Duration.ofMillis(10));
        CaseRun run = run(step);
        AssertionError last = new AssertionError("returns 2, expected 3");
        doThrow(new AssertionError("returns 1, expected 3"), last).when(mockExecutor).execute(run, step);

        assertThatThrownBy(() -> StepPolling.execute(mockExecutor, run, step))
                .hasMessageStartingWith("cases/root.yaml, case 'polling case', step 'main': still failing after 30 ms (")
                .hasMessageEndingWith("attempts); last failure: returns 2, expected 3")
                .hasCause(last);
    }

    @Test
    void execute_whenAPollingStepIsInvalid_shouldFailAtOnceWithoutRetrying() throws Exception {
        Step step = pollingStep(UNIT_CALL, Duration.ofSeconds(10), Duration.ofMillis(1));
        CaseRun run = run(step);
        CaseDefinitionException invalid = new CaseDefinitionException("no method 'run'");
        doThrow(invalid).when(mockExecutor).execute(run, step);

        assertThatThrownBy(() -> StepPolling.execute(mockExecutor, run, step)).isSameAs(invalid);

        verify(mockExecutor).execute(run, step);
    }

    private static Step pollingStep(Call call, Duration within, Duration every) {
        return new Step("main", call, StubSet.EMPTY, new Expect(null, Map.of(), null, null, List.of(),
                MissingNode.getInstance(), null, List.of(), List.of(), null, new Polling(within, every)));
    }

    private static CaseRun run(Step step) {
        return new CaseRun(new YamlCase("polling case", SOURCE, List.of(step), StubSet.EMPTY, StubSet.EMPTY, null,
                false));
    }
}
