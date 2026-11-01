package com.yamltestkit.infrastructure.mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.CheckResult;
import com.yamltestkit.domain.assertion.ExpectedValueResolver;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.AssertOperator;
import com.yamltestkit.domain.model.Assertion;
import com.yamltestkit.domain.model.CallCount;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.VerifyCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The verified object is a real Mockito mock of {@link EventSink}: it is the test data whose recorded calls the
 * verifier reads. Uses the real converter chain for the captured-call JSON (design D17).
 */
class MockVerifierTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main': verify[0] bean 'eventSink' send";
    private static final MethodRef SEND = new MethodRef("send", null);
    private static final Assertion FIRST_TOPIC = new Assertion("$[0].args[0]", false, null,
            Map.of(AssertOperator.EQUALS, TextNode.valueOf("responses")), null, null);
    private static final ArgumentMatcher<Object> ANY_ARGUMENT = argument -> true;
    private static final ArgumentMatcher<Object> RESPONSES_TOPIC = "responses"::equals;

    private ArgMatcherFactory mockArgMatchers;
    private AssertionEvaluator mockAssertions;
    private ExpectedValueResolver mockExpected;
    private EventSink eventSink;
    private StubErrors errors;
    private Method send;

    private MockVerifier underTest;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        mockArgMatchers = mock(ArgMatcherFactory.class);
        mockAssertions = mock(AssertionEvaluator.class);
        mockExpected = mock(ExpectedValueResolver.class);
        eventSink = Mockito.mock(EventSink.class);
        errors = new StubErrors();
        send = EventSink.class.getMethod("send", String.class, Object.class);
        when(mockArgMatchers.forMethod(null, send, EventSink.class, errors, WHERE))
                .thenReturn(List.of(ANY_ARGUMENT, ANY_ARGUMENT));
        underTest = new MockVerifier(ValueConverters.create(List.of()), mockArgMatchers, mockAssertions);
    }

    @Test
    void verify_whenTheMethodWasCalledTheExpectedNumberOfTimes_shouldPassWithoutCapturing() {
        eventSink.send("requests", new Event("r-1"));
        eventSink.flush();

        CheckResult result = underTest.verify(check(SEND, null, null, new CallCount.Times(1), null, List.of()),
                eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result).isEqualTo(new CheckResult(List.of(), null));
        verify(mockAssertions, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void verify_whenTheCountDiffers_shouldFailWithExpectedActualAndTheCallsOfTheMock() {
        eventSink.send("requests", new Event("r-1"));
        eventSink.flush();

        CheckResult result = underTest.verify(check(SEND, null, null, new CallCount.Times(2), null, List.of()),
                eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result.failures()).extracting(Throwable::getMessage).containsExactly(WHERE
                + ": expected times: 2, actual: 1; calls of the mock: [send[requests, Event[id=r-1]], flush[]]");
    }

    @Test
    void verify_whenNoCountIsGivenAndTheMethodWasNotCalled_shouldFailExpectingAtLeastOne() {
        CheckResult result = underTest.verify(check(SEND, null, null, CallCount.AT_LEAST_ONCE, null, List.of()),
                eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result.failures()).extracting(Throwable::getMessage)
                .containsExactly(WHERE + ": expected atLeast: 1, actual: 0; calls of the mock: none");
    }

    @Test
    void verify_whenArgsAreGiven_shouldCountOnlyCallsWhoseArgumentsMatch() {
        List<ArgMatcher> args = List.of(new ArgMatcher.Literal(TextNode.valueOf("responses")), new ArgMatcher.Any());
        when(mockExpected.resolve(TextNode.valueOf("responses"))).thenReturn(TextNode.valueOf("responses"));
        when(mockArgMatchers.forMethod(args, send, EventSink.class, errors, WHERE))
                .thenReturn(List.of(RESPONSES_TOPIC, ANY_ARGUMENT));
        eventSink.send("requests", new Event("r-1"));
        eventSink.send("responses", new Event("r-1"));

        CheckResult result = underTest.verify(check(SEND, args, null, new CallCount.Times(1), null, List.of()),
                eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void verify_whenAnArgsLiteralIsAReference_shouldMatchTheValueItPointsTo() {
        JsonNode reference = JsonNodeFactory.instance.objectNode().put("ref", "dsp");
        List<ArgMatcher> resolved = List.of(new ArgMatcher.Literal(TextNode.valueOf("responses")),
                new ArgMatcher.Any());
        when(mockExpected.resolve(reference)).thenReturn(TextNode.valueOf("responses"));
        when(mockArgMatchers.forMethod(resolved, send, EventSink.class, errors, WHERE))
                .thenReturn(List.of(RESPONSES_TOPIC, ANY_ARGUMENT));
        eventSink.send("responses", new Event("r-1"));

        CheckResult result = underTest.verify(check(SEND, List.of(new ArgMatcher.Literal(reference),
                new ArgMatcher.Any()), null, new CallCount.Times(1), null, List.of()), eventSink, EventSink.class,
                mockExpected, errors, WHERE);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void verify_whenAnArgsReferenceCantBeRead_shouldFailOnlyThisCheck() {
        JsonNode reference = JsonNodeFactory.instance.objectNode().put("ref", "dsp");
        AssertionFailedError unreadable = new AssertionFailedError("cases/a.yaml, case 'c': reference 'dsp': nothing"
                + " was recorded under this name");
        when(mockExpected.resolve(reference)).thenThrow(unreadable);

        CheckResult result = underTest.verify(check(SEND, List.of(new ArgMatcher.Literal(reference),
                new ArgMatcher.Any()), null, new CallCount.Times(1), null, List.of()), eventSink, EventSink.class,
                mockExpected, errors, WHERE);

        assertThat(result.failures()).singleElement().satisfies(failure -> assertThat(failure)
                .hasMessage(WHERE + ": args: " + unreadable.getMessage())
                .hasCause(unreadable));
        assertThat(result.captured()).isNull();
        verify(mockArgMatchers, never()).forMethod(any(), any(), any(), any(), any());
    }

    @Test
    void verify_whenArgTypeIsGiven_shouldKeepOnlyCallsWhoseArgumentIsAnInstanceOfTheType() {
        eventSink.send("responses", "plain text");
        eventSink.send("responses", new Event("r-1"));

        CheckResult result = underTest.verify(check(SEND, null, new VerifyCheck.ArgTypeFilter(1, Event.class.getName()),
                new CallCount.Times(1), "event", List.of()), eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result.captured()).isEqualTo(json("[{\"args\": [\"responses\", {\"id\": \"r-1\"}]}]"));
    }

    @Test
    void verify_whenArgTypeIndexIsOutOfRange_shouldFailAsAnInvalidCase() {
        VerifyCheck.OnMock check = check(SEND, null, new VerifyCheck.ArgTypeFilter(2, Event.class.getName()),
                new CallCount.Times(1), null, List.of());

        assertThatThrownBy(() -> underTest.verify(check, eventSink, EventSink.class, mockExpected, errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": argType.index 2 is out of range; send(String, Object) takes 2 arguments");
    }

    @Test
    void verify_whenTheMethodDoesNotExist_shouldFailAsAnInvalidCase() {
        VerifyCheck.OnMock check = check(new MethodRef("sned", null), null, null, new CallCount.Times(1), null,
                List.of());

        assertThatThrownBy(() -> underTest.verify(check, eventSink, EventSink.class, mockExpected, errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": " + EventSink.class.getName() + " has no method 'sned'");
    }

    @Test
    void verify_whenNeverAndOnlyAnotherMethodWasCalled_shouldPass() {
        eventSink.flush();

        CheckResult result = underTest.verify(check(SEND, null, null, new CallCount.Times(0), null, List.of()),
                eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void verify_whenNoInteractionsAndAnyMethodWasCalled_shouldFailListingTheCalls() {
        eventSink.flush();

        CheckResult result = underTest.verify(check(null, null, null, new CallCount.NoInteractions(), null,
                List.of()), eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result.failures()).extracting(Throwable::getMessage)
                .containsExactly(WHERE + ": expected none (noInteractions), actual: 1; calls of the mock: [flush[]]");
    }

    @Test
    void verify_whenTheCheckAssertsAndCaptures_shouldEvaluateTheAssertsOnTheCallsInCallOrder() {
        JsonNode captured = json("[{\"args\": [\"responses\", {\"id\": \"r-1\"}]},"
                + " {\"args\": [\"requests\", {\"id\": \"r-2\"}]}]");
        AssertionFailedError assertFailure = new AssertionFailedError(WHERE + ": assert `$[0].args[0]` equals");
        when(mockAssertions.evaluate(List.of(FIRST_TOPIC), captured, mockExpected, WHERE))
                .thenReturn(List.of(assertFailure));
        eventSink.send("responses", new Event("r-1"));
        eventSink.send("requests", new Event("r-2"));

        CheckResult result = underTest.verify(check(SEND, null, null, new CallCount.Times(2), "sent",
                List.of(FIRST_TOPIC)), eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result).isEqualTo(new CheckResult(List.of(assertFailure), captured));
    }

    @Test
    void verify_whenACapturedArgumentCantBeConverted_shouldFailSayingSo() {
        eventSink.send("responses", new Looping());

        CheckResult result = underTest.verify(check(SEND, null, null, new CallCount.Times(1), "sent", List.of()),
                eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result.captured()).isNull();
        assertThat(result.failures()).singleElement().extracting(Throwable::getMessage).asString()
                .startsWith(WHERE + ": a captured call can't be converted to JSON: can't convert a "
                        + Looping.class.getName() + " to JSON");
    }

    @Test
    void verify_whenAnArgsMatcherRecordedAnError_shouldReportIt() {
        errors.record(WHERE + ", args[1]: the actual argument can't be compared", new IllegalStateException("x"));
        eventSink.send("responses", new Event("r-1"));

        CheckResult result = underTest.verify(check(SEND, null, null, new CallCount.Times(1), null, List.of()),
                eventSink, EventSink.class, mockExpected, errors, WHERE);

        assertThat(result.failures()).extracting(Throwable::getMessage)
                .containsExactly(WHERE + ", args[1]: the actual argument can't be compared");
        assertThat(errors.drain()).isEmpty();
    }

    private static VerifyCheck.OnMock check(MethodRef method, List<ArgMatcher> args,
                                            VerifyCheck.ArgTypeFilter argType, CallCount count, String as,
                                            List<Assertion> asserts) {
        return new VerifyCheck.OnMock(VerifyCheck.MockTarget.BEAN, "eventSink", method, args, argType, count, null,
                as, asserts);
    }

    public interface EventSink {

        void send(String topic, Object event);

        void flush();
    }

    public record Event(String id) {
    }

    /** Refers to itself, so it can't be written as JSON. */
    public static final class Looping {

        @SuppressWarnings("unused")
        private final Looping self = this;
    }
}
