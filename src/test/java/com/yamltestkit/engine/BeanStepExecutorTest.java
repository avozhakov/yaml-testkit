package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.infrastructure.invocation.CallOutcome;
import com.yamltestkit.infrastructure.spring.CaseContext;
import com.yamltestkit.infrastructure.spring.RunningContext;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BeanStepExecutorTest {

    private static final CaseSource SOURCE = new CaseSource(URI.create("classpath:/cases/a.yaml"), "cases/", "a.yaml");
    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";
    private static final JsonNode THREE = IntNode.valueOf(3);
    private static final JsonNode REFERENCE_TO_STEP = JsonNodeFactory.instance.objectNode()
            .put("ref", Step.IMPLICIT_NAME);
    private static final Expect RETURNS_6 = new Expect(null, Map.of(), null, null, List.of(), IntNode.valueOf(6),
            null, List.of(), List.of(), null, null);

    private ValueConverters mockConverters;
    private ReturnAssertion mockReturns;
    private ConfigurableApplicationContext mockContext;
    private RunningContext mockRunning;
    private EffectChecks mockEffects;

    private BeanStepExecutor underTest;

    @BeforeEach
    void setUp() {
        mockConverters = mock(ValueConverters.class);
        mockReturns = mock(ReturnAssertion.class);
        mockContext = mock(ConfigurableApplicationContext.class);
        mockRunning = mock(RunningContext.class);
        mockEffects = mock(EffectChecks.class);
        when(mockRunning.context()).thenReturn(mockContext);
        underTest = new BeanStepExecutor(mockConverters, mockReturns, mockEffects);
    }

    @Test
    void execute_whenBeanIsPlain_shouldCallTheMethodWithConvertedArgsAndCheckTheOutcome() throws Exception {
        when(mockContext.getBean("doubler")).thenReturn(new DoublerImpl());
        when(mockConverters.fromYaml(THREE, int.class, WHERE + ": call.bean.args[0]")).thenReturn(3);
        CaseRun run = run(new MethodRef("twice", null), List.of(THREE), RETURNS_6);
        when(mockReturns.check(RETURNS_6, new CallOutcome.Returned(6), run, WHERE)).thenReturn(List.of());

        underTest.execute(run, run.yamlCase().steps().getFirst());

        verify(mockReturns).check(RETURNS_6, new CallOutcome.Returned(6), run, WHERE);
    }

    @Test
    void execute_whenBeanIsAJdkProxy_shouldCallTheMethodThroughTheProxy() throws Exception {
        ProxyFactory proxyFactory = new ProxyFactory(new DoublerImpl());
        proxyFactory.addInterface(Doubler.class);
        when(mockContext.getBean("doubler")).thenReturn(proxyFactory.getProxy());
        when(mockConverters.fromYaml(THREE, int.class, WHERE + ": call.bean.args[0]")).thenReturn(3);
        CaseRun run = run(new MethodRef("twice", null), List.of(THREE), RETURNS_6);
        when(mockReturns.check(RETURNS_6, new CallOutcome.Returned(6), run, WHERE)).thenReturn(List.of());

        underTest.execute(run, run.yamlCase().steps().getFirst());

        verify(mockReturns).check(RETURNS_6, new CallOutcome.Returned(6), run, WHERE);
    }

    @Test
    void execute_whenEffectChecksAndExpectationsFail_shouldReportBothAndKeepTheResultForReferences() throws Exception {
        when(mockContext.getBean("doubler")).thenReturn(new DoublerImpl());
        when(mockConverters.fromYaml(THREE, int.class, WHERE + ": call.bean.args[0]")).thenReturn(3);
        when(mockConverters.toJson(6)).thenReturn(IntNode.valueOf(6));
        CaseRun run = run(new MethodRef("twice", null), List.of(THREE), RETURNS_6);
        Step step = run.yamlCase().steps().getFirst();
        AssertionFailedError verifyFailure = new AssertionFailedError(WHERE + ": verify[0] bean 'auditService'");
        AssertionFailedError returnsFailure = new AssertionFailedError(WHERE + ": returns differs");
        when(mockEffects.check(run, step)).thenReturn(List.of(verifyFailure));
        when(mockReturns.check(RETURNS_6, new CallOutcome.Returned(6), run, WHERE)).thenReturn(List.of(returnsFailure));

        assertThatThrownBy(() -> underTest.execute(run, step))
                .isInstanceOfSatisfying(MultipleFailuresError.class, error -> assertThat(error.getFailures())
                        .containsExactly(verifyFailure, returnsFailure));
        assertThat(run.resolve(REFERENCE_TO_STEP)).isEqualTo(IntNode.valueOf(6));
    }

    @Test
    void execute_whenTheMethodThrows_shouldHandTheExceptionToTheExpectations() throws Exception {
        when(mockContext.getBean("doubler")).thenReturn(new DoublerImpl());
        when(mockConverters.fromYaml(TextNode.valueOf("db down"), String.class, WHERE + ": call.bean.args[0]"))
                .thenReturn("db down");
        CaseRun run = run(new MethodRef("fail", null), List.of(TextNode.valueOf("db down")), Expect.NONE);
        AssertionFailedError failure = new AssertionFailedError(WHERE + ": call threw");
        when(mockReturns.check(Expect.NONE, new CallOutcome.Thrown(DoublerImpl.DB_DOWN), run, WHERE))
                .thenReturn(List.of(failure));

        assertThatThrownBy(() -> underTest.execute(run, run.yamlCase().steps().getFirst())).isSameAs(failure);
    }

    @Test
    void execute_whenTheBeanDoesNotExist_shouldFailNamingIt() {
        when(mockContext.getBean("doubler")).thenThrow(new NoSuchBeanDefinitionException("doubler"));
        CaseRun run = run(new MethodRef("twice", null), List.of(THREE), RETURNS_6);

        assertThatThrownBy(() -> underTest.execute(run, run.yamlCase().steps().getFirst()))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": call.bean: the context has no bean named 'doubler'");
    }

    @Test
    void execute_whenArgsCountDiffers_shouldFailNamingTheSignature() {
        when(mockContext.getBean("doubler")).thenReturn(new DoublerImpl());
        CaseRun run = run(new MethodRef("twice", null), List.of(), RETURNS_6);

        assertThatThrownBy(() -> underTest.execute(run, run.yamlCase().steps().getFirst()))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": twice(int) takes 1 arguments, but `args` has 0");
        assertThat(run.stubErrors().drain()).isEmpty();
    }

    private CaseRun run(MethodRef method, List<JsonNode> args, Expect expect) {
        Step step = new Step(Step.IMPLICIT_NAME, new Call.Bean("doubler", method, args), StubSet.EMPTY, expect);
        CaseRun run = new CaseRun(new YamlCase("c", SOURCE, List.of(step), StubSet.EMPTY, StubSet.EMPTY, null, false));
        run.attach(new CaseContext(mockRunning, mock(WireMockServices.class), false));
        return run;
    }

    public interface Doubler {

        int twice(int value);

        String fail(String message);
    }

    public static final class DoublerImpl implements Doubler {

        static final IllegalStateException DB_DOWN = new IllegalStateException("db down");

        @Override
        public int twice(int value) {
            return value * 2;
        }

        @Override
        public String fail(String message) {
            throw DB_DOWN;
        }
    }
}
