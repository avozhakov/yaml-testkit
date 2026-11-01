package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.BodyAssertion;
import com.yamltestkit.domain.assertion.TextAssertion;
import com.yamltestkit.domain.model.AssertOperator;
import com.yamltestkit.domain.model.Assertion;
import com.yamltestkit.domain.model.BodyExpectation;
import com.yamltestkit.domain.model.BodyMode;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.Query;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.TextExpectation;
import com.yamltestkit.domain.model.TextOperator;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.infrastructure.spring.CaseContext;
import com.yamltestkit.infrastructure.spring.RunningContext;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpStepExecutorTest {

    private static final CaseSource SOURCE = new CaseSource(URI.create("classpath:/cases/a.yaml"), "cases/", "a.yaml");
    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";
    private static final int PORT = 8123;
    private static final String BODY = "{\"ads\":[1,2]}";
    private static final JsonNode BODY_JSON = JsonNodeFactory.instance.objectNode().set("ads",
            JsonNodeFactory.instance.arrayNode().add(1).add(2));
    private static final JsonNode REFERENCE_TO_ADS = JsonNodeFactory.instance.objectNode()
            .put("ref", Step.IMPLICIT_NAME).put("path", "$.ads");
    private static final JsonNode UNKNOWN_REFERENCE = JsonNodeFactory.instance.objectNode().put("ref", "logged");
    private static final Call.Http CALL = new Call.Http("GET", "/is", Query.NONE, Map.of(), MissingNode.getInstance());
    private static final BodyExpectation BODY_EXPECTATION = new BodyExpectation(BODY_JSON, BodyMode.LENIENT, List.of());
    private static final TextExpectation TEXT_EXPECTATION = new TextExpectation(Map.of(TextOperator.CONTAINS,
            List.of("ads")));
    private static final Assertion SIZE_TWO = new Assertion("$.ads", false, null, Map.of(AssertOperator.SIZE,
            IntNode.valueOf(2)), null, null);

    private HttpClient mockClient;
    private BodyAssertion mockBodies;
    private TextAssertion mockTexts;
    private AssertionEvaluator mockAssertions;
    private RunningContext mockRunning;
    private EffectChecks mockEffects;
    private HttpResponse<String> mockResponse;

    private HttpStepExecutor underTest;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        mockClient = mock(HttpClient.class);
        mockBodies = mock(BodyAssertion.class);
        mockTexts = mock(TextAssertion.class);
        mockAssertions = mock(AssertionEvaluator.class);
        mockRunning = mock(RunningContext.class);
        mockEffects = mock(EffectChecks.class);
        mockResponse = mock(HttpResponse.class);
        when(mockRunning.requirePort(WHERE)).thenReturn(PORT);
        when(mockClient.send(argThat(request -> request.uri().equals(URI.create("http://localhost:8123/is"))),
                ArgumentMatchers.<HttpResponse.BodyHandler<String>>notNull())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(BODY);
        when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of("X-Echo", List.of("hello")),
                (name, value) -> true));
        underTest = new HttpStepExecutor(mockClient, mockBodies, mockTexts, mockAssertions, mockEffects);
    }

    @Test
    void close_whenCalled_shouldCloseTheHttpClient() {
        underTest.close();

        verify(mockClient).close();
    }

    @Test
    void execute_whenExpectationsHold_shouldCheckEveryPartOfTheResponse() throws Exception {
        Expect expect = expect(200, Map.of("x-echo", "hello"), BODY_EXPECTATION, TEXT_EXPECTATION, List.of(SIZE_TWO));
        CaseRun run = run(expect);
        when(mockBodies.check(BODY_EXPECTATION, BODY_JSON, BODY, WHERE)).thenReturn(List.of());
        when(mockTexts.check(TEXT_EXPECTATION, BODY, WHERE)).thenReturn(List.of());
        when(mockAssertions.evaluate(List.of(SIZE_TWO), BODY_JSON, run, WHERE)).thenReturn(List.of());

        underTest.execute(run, run.yamlCase().steps().getFirst());

        verify(mockBodies).check(BODY_EXPECTATION, BODY_JSON, BODY, WHERE);
        verify(mockTexts).check(TEXT_EXPECTATION, BODY, WHERE);
        verify(mockAssertions).evaluate(List.of(SIZE_TWO), BODY_JSON, run, WHERE);
    }

    @Test
    void execute_whenEffectChecksFail_shouldReportThemWithTheResponseChecksAndKeepTheBodyForReferences() {
        CaseRun run = run(expect(201, Map.of(), null, null, List.of()));
        Step step = run.yamlCase().steps().getFirst();
        AssertionFailedError verifyFailure = new AssertionFailedError(WHERE + ": verify[0] service 'partner'");
        when(mockEffects.check(run, step)).thenReturn(List.of(verifyFailure));

        assertThatThrownBy(() -> underTest.execute(run, step))
                .isInstanceOfSatisfying(MultipleFailuresError.class, error -> assertThat(error.getFailures())
                        .extracting(Throwable::getMessage)
                        .containsExactly(WHERE + ": verify[0] service 'partner'",
                                WHERE + ": status expected 201 but was 200; body: " + BODY));
        assertThat(run.resolve(REFERENCE_TO_ADS)).isEqualTo(BODY_JSON.get("ads"));
    }

    @Test
    void execute_whenTheBodyReferenceCantBeRead_shouldReportItWithTheOtherFailures() {
        BodyExpectation byReference = new BodyExpectation(UNKNOWN_REFERENCE, BodyMode.LENIENT, List.of());
        CaseRun run = run(expect(201, Map.of(), byReference, null, List.of()));
        Step step = run.yamlCase().steps().getFirst();
        AssertionFailedError verifyFailure = new AssertionFailedError(WHERE + ": verify[0] bean 'eventLogger'");
        when(mockEffects.check(run, step)).thenReturn(List.of(verifyFailure));

        assertThatThrownBy(() -> underTest.execute(run, step))
                .isInstanceOfSatisfying(MultipleFailuresError.class, error -> assertThat(error.getFailures())
                        .extracting(Throwable::getMessage)
                        .containsExactly(WHERE + ": verify[0] bean 'eventLogger'",
                                WHERE + ": status expected 201 but was 200; body: " + BODY,
                                "cases/a.yaml, case 'c': reference 'logged': nothing was recorded under this name"));
        verify(mockBodies, never()).check(any(), any(), any(), any());
    }

    @Test
    void execute_whenTheBodyIsNotJson_shouldKeepItAsTextForReferences() throws Exception {
        when(mockResponse.body()).thenReturn("<xml/>");
        CaseRun run = run(Expect.NONE);

        underTest.execute(run, run.yamlCase().steps().getFirst());

        assertThat(run.resolve(JsonNodeFactory.instance.objectNode().put("ref", Step.IMPLICIT_NAME)))
                .isEqualTo(TextNode.valueOf("<xml/>"));
    }

    @Test
    void execute_whenStatusAndHeadersDiffer_shouldReportBothWithExpectedAndActual() {
        CaseRun run = run(expect(201, Map.of("X-Echo", "bye", "X-Missing", "1"), null, null, List.of()));

        assertThatThrownBy(() -> underTest.execute(run, run.yamlCase().steps().getFirst()))
                .isInstanceOfSatisfying(MultipleFailuresError.class, error -> assertThat(error.getFailures())
                        .extracting(Throwable::getMessage)
                        .containsExactlyInAnyOrder(
                                WHERE + ": status expected 201 but was 200; body: " + BODY,
                                WHERE + ": header X-Echo expected \"bye\" but was \"hello\"",
                                WHERE + ": header X-Missing expected \"1\" but the response has no such header;"
                                        + " headers: X-Echo"));
        verify(mockBodies, never()).check(any(), any(), any(), any());
    }

    @Test
    void execute_whenAssertGetsANonJsonBody_shouldFailSayingSo() {
        when(mockResponse.body()).thenReturn("<xml/>");
        CaseRun run = run(expect(null, Map.of(), null, null, List.of(SIZE_TWO)));

        assertThatThrownBy(() -> underTest.execute(run, run.yamlCase().steps().getFirst()))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessageStartingWith(WHERE + ": assert needs a JSON body, but the body is not JSON (")
                .hasMessageEndingWith("): <xml/>");
    }

    private CaseRun run(Expect expect) {
        Step step = new Step(Step.IMPLICIT_NAME, CALL, StubSet.EMPTY, expect);
        CaseRun run = new CaseRun(new YamlCase("c", SOURCE, List.of(step), StubSet.EMPTY, StubSet.EMPTY, null, false));
        run.attach(new CaseContext(mockRunning, mock(WireMockServices.class), false));
        return run;
    }

    private static Expect expect(Integer status, Map<String, String> headers, BodyExpectation body,
                                 TextExpectation text, List<Assertion> asserts) {
        return new Expect(status, headers, body, text, asserts, MissingNode.getInstance(), null, List.of(), List.of(),
                null, null);
    }
}
