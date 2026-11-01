package com.yamltestkit.domain.assertion;

import com.yamltestkit.domain.model.TextExpectation;
import com.yamltestkit.domain.model.TextOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TextAssertionTest {

    private static final String WHERE = "cases/a.yaml, case 'text', step 'main'";
    private static final String BODY = "<ads><ad url=\"https://x\"/></ads>";

    private TextAssertion underTest;

    @BeforeEach
    void setUp() {
        underTest = new TextAssertion();
    }

    @Test
    void check_whenEveryMatcherHolds_shouldPass() {
        TextExpectation text = new TextExpectation(Map.of(
                TextOperator.CONTAINS, List.of("<ad ", "url="),
                TextOperator.NOT_CONTAINS, List.of("asin", "gtin"),
                TextOperator.STARTS_WITH, List.of("<ads>"),
                TextOperator.MATCHES, List.of("<ads>.*</ads>"),
                TextOperator.EQUALS, List.of(BODY)));

        assertThat(underTest.check(text, BODY, WHERE)).isEmpty();
    }

    @Test
    void check_whenSomeValuesOfAMatcherFail_shouldReportEachOne() {
        TextExpectation text = new TextExpectation(Map.of(
                TextOperator.NOT_CONTAINS, List.of("asin", "url"),
                TextOperator.CONTAINS, List.of("gtin")));

        List<AssertionFailedError> result = underTest.check(text, BODY, WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": bodyText contains \"gtin\" does not hold for body \"" + BODY + "\"",
                WHERE + ": bodyText notContains \"url\" does not hold for body \"" + BODY + "\"");
    }

    @Test
    void check_whenTheBodyIsLong_shouldShortenItInTheMessageButKeepItWholeAsTheActualValue() {
        String longBody = "x".repeat(1_000);
        TextExpectation text = new TextExpectation(Map.of(TextOperator.CONTAINS, List.of("y")));

        List<AssertionFailedError> result = underTest.check(text, longBody, WHERE);

        assertThat(result).singleElement().satisfies(failure -> {
            assertThat(failure).hasMessage(WHERE + ": bodyText contains \"y\" does not hold for body \""
                    + "x".repeat(300) + "…\"");
            assertThat(failure.getActual().getValue()).isEqualTo(longBody);
        });
    }
}
