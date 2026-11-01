package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.model.BodyExpectation;
import com.yamltestkit.domain.model.BodyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

class BodyAssertionTest {

    private static final String WHERE = "cases/a.yaml, case 'golden', step 'main'";
    private static final String ACTUAL = "{\"requestId\": \"r-2\", \"ads\": [{\"id\": 1, \"rank\": 1}, {\"id\": 2}]}";
    private static final JsonNode GOLDEN = json("{\"requestId\": \"r-1\", \"ads\": [{\"id\": 1}, {\"id\": 2}]}");

    private BodyAssertion underTest;

    @BeforeEach
    void setUp() {
        underTest = new BodyAssertion();
    }

    @Test
    void check_whenOnlyIgnoredFieldsDiffer_shouldPass() {
        BodyExpectation body = new BodyExpectation(GOLDEN, BodyMode.LENIENT, List.of("$.requestId"));

        assertThat(underTest.check(body, GOLDEN, ACTUAL, WHERE)).isEmpty();
    }

    @Test
    void check_whenAnIgnoredFieldIsMissing_shouldStillPass() {
        BodyExpectation body = new BodyExpectation(GOLDEN, BodyMode.LENIENT, List.of("$.requestId", "$.nope"));

        assertThat(underTest.check(body, GOLDEN, ACTUAL, WHERE)).isEmpty();
    }

    @Test
    void check_whenModeIsStrict_shouldReportExtraActualFields() {
        BodyExpectation body = new BodyExpectation(GOLDEN, BodyMode.STRICT, List.of("$.requestId"));

        assertThat(underTest.check(body, GOLDEN, ACTUAL, WHERE)).singleElement().extracting(Throwable::getMessage)
                .asString().startsWith(WHERE + ": body (strict) differs: ").contains("$.ads[0].rank: unexpected field with value 1");
    }

    @Test
    void check_whenArrayOrderDiffers_shouldFailByDefault() {
        JsonNode reversed = json("{\"ads\": [{\"id\": 2}, {\"id\": 1}]}");
        BodyExpectation body = new BodyExpectation(reversed, BodyMode.LENIENT, List.of());

        assertThat(underTest.check(body, reversed, ACTUAL, WHERE)).singleElement().extracting(Throwable::getMessage)
                .asString().contains("ads[0].id");
    }

    @Test
    void check_whenExactTextDiffersOnlyInWhitespace_shouldFail() {
        TextNode golden = TextNode.valueOf("{\"a\":1}");
        BodyExpectation body = new BodyExpectation(golden, BodyMode.EXACT, List.of());

        assertThat(underTest.check(body, golden, "{\"a\": 1}", WHERE)).singleElement()
                .extracting(Throwable::getMessage).asString().isEqualTo(WHERE + ": body (exact) differs");
        assertThat(underTest.check(body, golden, "{\"a\":1}", WHERE)).isEmpty();
    }

    @Test
    void check_whenBodyIsNotJson_shouldSaySo() {
        BodyExpectation body = new BodyExpectation(GOLDEN, BodyMode.LENIENT, List.of());

        assertThat(underTest.check(body, GOLDEN, "<url>", WHERE)).singleElement().extracting(Throwable::getMessage)
                .asString().startsWith(WHERE + ": body is not JSON");
    }
}
