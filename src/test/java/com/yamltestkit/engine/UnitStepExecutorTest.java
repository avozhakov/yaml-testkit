package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.Dependency;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.infrastructure.invocation.CallOutcome;
import com.yamltestkit.infrastructure.mockito.UnitInstance;
import com.yamltestkit.infrastructure.mockito.UnitInstanceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opentest4j.AssertionFailedError;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnitStepExecutorTest {

    private static final CaseSource SOURCE = new CaseSource(URI.create("classpath:/cases/a.yaml"), "cases/", "a.yaml");
    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";
    private static final String CALCULATOR = Calculator.class.getName();
    private static final JsonNode TWENTY_ONE = IntNode.valueOf(21);
    private static final JsonNode REFERENCE_TO_STEP = JsonNodeFactory.instance.objectNode()
            .put("ref", Step.IMPLICIT_NAME);
    private static final Expect RETURNS_42 = new Expect(null, Map.of(), null, null, List.of(), IntNode.valueOf(42),
            null, List.of(), List.of(), null, null);

    private ValueConverters mockConverters;
    private UnitInstanceFactory mockInstances;
    private ReturnAssertion mockReturns;
    private EffectChecks mockEffects;

    private UnitStepExecutor underTest;

    @BeforeEach
    void setUp() {
        mockConverters = mock(ValueConverters.class);
        mockInstances = mock(UnitInstanceFactory.class);
        mockReturns = mock(ReturnAssertion.class);
        mockEffects = mock(EffectChecks.class);
        underTest = new UnitStepExecutor(mockConverters, mockInstances, mockReturns, mockEffects);
    }

    @Test
    void execute_whenMethodIsStatic_shouldCallItWithConvertedArgsAndNoInstance() throws Exception {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("twice", null), List.of(TWENTY_ONE), null,
                Map.of()), RETURNS_42);
        CaseRun run = run(step);
        when(mockConverters.fromYaml(TWENTY_ONE, int.class, WHERE + ": call.class.args[0]")).thenReturn(21);

        underTest.execute(run, step);

        verify(mockReturns).check(RETURNS_42, new CallOutcome.Returned(42), run, WHERE);
        verify(mockInstances, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void execute_whenMethodThrows_shouldHandTheExceptionToTheExpectations() throws Exception {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("fail", null), List.of(TextNode.valueOf("db down")),
                null, Map.of()), Expect.NONE);
        ArgumentCaptor<CallOutcome> outcome = ArgumentCaptor.forClass(CallOutcome.class);
        CaseRun run = run(step);
        when(mockInstances.create(Calculator.class, null, Map.of(), run.stubErrors(), WHERE + ": call.class"))
                .thenReturn(new UnitInstance(new Calculator(), Map.of()));
        when(mockConverters.fromYaml(TextNode.valueOf("db down"), String.class, WHERE + ": call.class.args[0]"))
                .thenReturn("db down");

        underTest.execute(run, step);

        verify(mockReturns).check(eq(Expect.NONE), outcome.capture(), eq(run), eq(WHERE));
        assertThat(outcome.getValue()).isInstanceOfSatisfying(CallOutcome.Thrown.class,
                thrown -> assertThat(thrown.exception()).isInstanceOf(IllegalStateException.class)
                        .hasMessage("db down"));
    }

    @Test
    void execute_whenExpectationsFail_shouldThrowTheFailure() throws Exception {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("twice", null), List.of(TWENTY_ONE), null,
                Map.of()), RETURNS_42);
        AssertionFailedError failure = new AssertionFailedError(WHERE + ": returns differs");
        CaseRun run = run(step);
        when(mockConverters.fromYaml(TWENTY_ONE, int.class, WHERE + ": call.class.args[0]")).thenReturn(21);
        when(mockReturns.check(RETURNS_42, new CallOutcome.Returned(42), run, WHERE)).thenReturn(List.of(failure));

        assertThatThrownBy(() -> underTest.execute(run, step)).isSameAs(failure);
    }

    @Test
    void execute_whenEffectChecksFail_shouldReportThemBeforeTheExpectationsAndKeepTheDepMocks() throws Exception {
        Object mockClock = mock(Runnable.class);
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("fail", null), List.of(TextNode.valueOf("db down")),
                null, Map.of()), Expect.NONE);
        CaseRun run = run(step);
        when(mockInstances.create(Calculator.class, null, Map.of(), run.stubErrors(), WHERE + ": call.class"))
                .thenReturn(new UnitInstance(new Calculator(), Map.of("clock", mockClock)));
        when(mockConverters.fromYaml(TextNode.valueOf("db down"), String.class, WHERE + ": call.class.args[0]"))
                .thenReturn("db down");
        AssertionFailedError verifyFailure = new AssertionFailedError(WHERE + ": verify[0] dep 'clock' run");
        when(mockEffects.check(run, step)).thenReturn(List.of(verifyFailure));

        assertThatThrownBy(() -> underTest.execute(run, step)).isSameAs(verifyFailure);
        assertThat(run.unitMocks(Step.IMPLICIT_NAME)).containsExactly(Map.entry("clock", mockClock));
        assertThatThrownBy(() -> run.resolve(REFERENCE_TO_STEP))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessage("cases/a.yaml, case 'c': reference 'main': no value: the call threw"
                        + " java.lang.IllegalStateException: db down");
    }

    @Test
    void execute_whenADepStubRecordedAnError_shouldReportItWithTheExpectations() {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("twice", null), List.of(TWENTY_ONE), null,
                Map.of()), RETURNS_42);
        CaseRun run = run(step);
        IllegalStateException cause = new IllegalStateException("can't convert");
        run.stubErrors().record(WHERE + ", deps.rates, stub rate, args[0]: not comparable", cause);
        when(mockConverters.fromYaml(TWENTY_ONE, int.class, WHERE + ": call.class.args[0]")).thenReturn(21);

        assertThatThrownBy(() -> underTest.execute(run, step))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessage(WHERE + ", deps.rates, stub rate, args[0]: not comparable")
                .hasCause(cause);
        assertThat(run.stubErrors().drain()).isEmpty();
    }

    @Test
    void execute_whenStaticMethodHasDeps_shouldFailSayingNoInstanceIsCreated() {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("twice", null), List.of(TWENTY_ONE), null,
                Map.of("clock", new Dependency.Real())), Expect.NONE);

        assertThatThrownBy(() -> underTest.execute(run(step), step))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": static twice(int) is static, so no instance is created; remove `constructor` /"
                        + " `deps`");
    }

    @Test
    void execute_whenStaticMethodHasAConstructor_shouldFailSayingNoInstanceIsCreated() {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("twice", null), List.of(TWENTY_ONE),
                List.of("int"), Map.of()), Expect.NONE);

        assertThatThrownBy(() -> underTest.execute(run(step), step))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": static twice(int) is static, so no instance is created; remove `constructor` /"
                        + " `deps`");
    }

    @Test
    void execute_whenReturnedFutureCompletesExceptionally_shouldHandItsCauseToTheExpectations() throws Exception {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("failLater", null), List.of(), null, Map.of()),
                Expect.NONE);
        ArgumentCaptor<CallOutcome> outcome = ArgumentCaptor.forClass(CallOutcome.class);
        CaseRun run = run(step);
        when(mockInstances.create(Calculator.class, null, Map.of(), run.stubErrors(), WHERE + ": call.class"))
                .thenReturn(new UnitInstance(new Calculator(), Map.of()));

        underTest.execute(run, step);

        verify(mockReturns).check(eq(Expect.NONE), outcome.capture(), eq(run), eq(WHERE));
        assertThat(outcome.getValue()).isEqualTo(new CallOutcome.Thrown(Calculator.TIMEOUT));
    }

    @Test
    void execute_whenInstanceHasDepMocks_shouldKeepThemInTheRunByStep() throws Exception {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("fail", null), List.of(TextNode.valueOf("x")),
                null, Map.of()), Expect.NONE);
        CaseRun run = run(step);
        Object mockClock = mock(Runnable.class);
        when(mockInstances.create(Calculator.class, null, Map.of(), run.stubErrors(), WHERE + ": call.class"))
                .thenReturn(new UnitInstance(new Calculator(), Map.of("clock", mockClock)));
        when(mockConverters.fromYaml(TextNode.valueOf("x"), String.class, WHERE + ": call.class.args[0]"))
                .thenReturn("x");

        underTest.execute(run, step);

        assertThat(run.unitMocks(Step.IMPLICIT_NAME)).containsExactly(Map.entry("clock", mockClock));
    }

    @Test
    void execute_whenArgsCountDiffers_shouldFailNamingTheSignature() {
        Step step = step(new Call.Unit(CALCULATOR, new MethodRef("twice", null), List.of(), null, Map.of()),
                Expect.NONE);

        assertThatThrownBy(() -> underTest.execute(run(step), step))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": static twice(int) takes 1 arguments, but `args` has 0");
    }

    private static Step step(Call.Unit call, Expect expect) {
        return new Step(Step.IMPLICIT_NAME, call, StubSet.EMPTY, expect);
    }

    private static CaseRun run(Step step) {
        return new CaseRun(new YamlCase("c", SOURCE, List.of(step), StubSet.EMPTY, StubSet.EMPTY, null, false));
    }

    public static final class Calculator {

        public static int twice(int value) {
            return value * 2;
        }

        static final IllegalStateException TIMEOUT = new IllegalStateException("partner timed out");

        public int fail(String message) {
            throw new IllegalStateException(message);
        }

        public CompletableFuture<Integer> failLater() {
            return CompletableFuture.failedFuture(TIMEOUT);
        }
    }
}
