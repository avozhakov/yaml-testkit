package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.ExpectedValueResolver;
import com.yamltestkit.domain.model.AssertOperator;
import com.yamltestkit.domain.model.Assertion;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.ExpectedThrow;
import com.yamltestkit.infrastructure.invocation.CallOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uses the real converter chain (design D17): it maps values to JSON deterministically, like test data, and mocking
 * it would only restate that JSON.
 */
class ReturnAssertionTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";
    private static final Provider AMAZON = new Provider("amazon", 4);
    private static final JsonNode AMAZON_JSON = json("{\"name\": \"amazon\", \"maxAds\": 4}");
    private static final JsonNode AMAZON_NAME = JsonNodeFactory.instance.objectNode().put("name", "amazon");
    private static final JsonNode REFERENCE = JsonNodeFactory.instance.objectNode().put("ref", "logged");
    private static final ExpectedValueResolver AS_WRITTEN = expected -> expected;
    private static final IllegalStateException DB_DOWN = new IllegalStateException("db down");
    private static final Assertion MAX_ADS = new Assertion("$.maxAds", false, null,
            Map.of(AssertOperator.GTE, IntNode.valueOf(5)), null, null);

    private AssertionEvaluator mockAssertions;

    private ReturnAssertion underTest;

    @BeforeEach
    void setUp() {
        mockAssertions = mock(AssertionEvaluator.class);
        underTest = new ReturnAssertion(ValueConverters.create(List.of()), mockAssertions);
    }

    @Test
    void check_whenReturnedValueMatchesLeniently_shouldPass() {
        List<AssertionFailedError> result = underTest.check(expect(AMAZON_NAME, null, List.of()),
                new CallOutcome.Returned(AMAZON), AS_WRITTEN, WHERE);

        assertThat(result).isEmpty();
    }

    @Test
    void check_whenReturnedValueDiffers_shouldReportTheDifference() {
        List<AssertionFailedError> result = underTest.check(expect(IntNode.valueOf(5), null, List.of()),
                new CallOutcome.Returned(4), AS_WRITTEN, WHERE);

        assertThat(result).singleElement().extracting(Throwable::getMessage)
                .isEqualTo(WHERE + ": returns differs: expected 5 but was 4");
    }

    @Test
    void check_whenTheReturnsReferenceCantBeRead_shouldReportItWithTheAssertFailures() {
        AssertionFailedError unreadable = new AssertionFailedError(WHERE + ": reference 'logged': nothing recorded");
        AssertionFailedError assertFailure = new AssertionFailedError(WHERE + ": assert `$.maxAds` gte");
        ExpectedValueResolver failing = expected -> {
            throw unreadable;
        };
        when(mockAssertions.evaluate(List.of(MAX_ADS), AMAZON_JSON, failing, WHERE)).thenReturn(List.of(assertFailure));

        List<AssertionFailedError> result = underTest.check(expect(REFERENCE, null, List.of(MAX_ADS)),
                new CallOutcome.Returned(AMAZON), failing, WHERE);

        assertThat(result).containsExactly(unreadable, assertFailure);
    }

    @Test
    void check_whenAssertEntriesAreGiven_shouldEvaluateThemOnTheReturnedJson() {
        AssertionFailedError failure = new AssertionFailedError(WHERE + ": assert `$.maxAds` gte");
        when(mockAssertions.evaluate(List.of(MAX_ADS), AMAZON_JSON, AS_WRITTEN, WHERE)).thenReturn(List.of(failure));

        List<AssertionFailedError> result = underTest.check(expect(MissingNode.getInstance(), null,
                List.of(MAX_ADS)), new CallOutcome.Returned(AMAZON), AS_WRITTEN, WHERE);

        assertThat(result).containsExactly(failure);
    }

    @Test
    void check_whenExpectedExceptionIsThrown_shouldPassForSimpleNameAndSuperclass() {
        List<AssertionFailedError> bySimpleName = underTest.check(
                expect(MissingNode.getInstance(), new ExpectedThrow("IllegalStateException", "db down"), List.of()),
                new CallOutcome.Thrown(DB_DOWN), AS_WRITTEN, WHERE);
        List<AssertionFailedError> bySuperclass = underTest.check(
                expect(MissingNode.getInstance(), new ExpectedThrow("java.lang.RuntimeException", null), List.of()),
                new CallOutcome.Thrown(DB_DOWN), AS_WRITTEN, WHERE);

        assertThat(bySimpleName).isEmpty();
        assertThat(bySuperclass).isEmpty();
        verify(mockAssertions, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void check_whenThrownTypeAndMessageDiffer_shouldReportBoth() {
        List<AssertionFailedError> result = underTest.check(
                expect(MissingNode.getInstance(), new ExpectedThrow("IllegalArgumentException", "nope"), List.of()),
                new CallOutcome.Thrown(DB_DOWN), AS_WRITTEN, WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": throws: expected IllegalArgumentException but java.lang.IllegalStateException was thrown:"
                        + " db down",
                WHERE + ": throws: expected message \"nope\" but was \"db down\"");
    }

    @Test
    void check_whenAnExceptionIsExpectedButTheCallReturns_shouldSayNoExceptionWasThrown() {
        List<AssertionFailedError> result = underTest.check(
                expect(MissingNode.getInstance(), new ExpectedThrow("IllegalStateException", null), List.of()),
                new CallOutcome.Returned(true), AS_WRITTEN, WHERE);

        assertThat(result).singleElement().extracting(Throwable::getMessage).isEqualTo(WHERE
                + ": throws: expected IllegalStateException to be thrown, but no exception was thrown (returned true)");
    }

    @Test
    void check_whenTheCallThrowsUnexpectedly_shouldFailWithTheLocationAndTheExceptionAsCause() {
        List<AssertionFailedError> result = underTest.check(expect(BooleanNode.TRUE, null, List.of()),
                new CallOutcome.Thrown(DB_DOWN), AS_WRITTEN, WHERE);

        assertThat(result).singleElement().satisfies(failure -> {
            assertThat(failure).hasMessage(WHERE + ": call threw java.lang.IllegalStateException: db down");
            assertThat(failure.getCause()).isSameAs(DB_DOWN);
        });
    }

    @Test
    void check_whenTheReturnedValueCantBeConverted_shouldFailWithTheLocation() {
        List<AssertionFailedError> result = underTest.check(expect(BooleanNode.TRUE, null, List.of()),
                new CallOutcome.Returned(CompletableFuture.failedFuture(DB_DOWN)), AS_WRITTEN, WHERE);

        assertThat(result).singleElement().extracting(Throwable::getMessage).asString()
                .startsWith(WHERE + ": the returned value can't be checked: ")
                .contains("CompletableFuture completed exceptionally");
    }

    private static Expect expect(JsonNode returns, ExpectedThrow throwsException, List<Assertion> asserts) {
        return new Expect(null, Map.of(), null, null, asserts, returns, throwsException, List.of(), List.of(), null,
                null);
    }

    record Provider(String name, int maxAds) {
    }
}
