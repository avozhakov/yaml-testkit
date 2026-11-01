package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.yamltestkit.domain.model.AssertOperator;
import com.yamltestkit.domain.model.Assertion;
import com.yamltestkit.domain.model.BodyMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

class AssertionEvaluatorTest {

    private static final String WHERE = "cases/a.yaml, case 'ads', step 'main'";
    private static final JsonNode DOCUMENT = json("""
            {
              "requestId": "req-42",
              "gtin": null,
              "ads": [
                { "title": "Red shoes", "qt": "shoes", "section": "top", "price": 120 },
                { "title": "Blue shoes", "qt": "shoes", "section": "side", "price": 80 },
                { "title": "Socks", "qt": "socks", "section": "top", "price": 15, "asin": "B00SOCKS" }
              ],
              "diag": [ { "step": "dedup", "payload": "{\\"dropped\\": [\\"ad-9\\"]}" } ],
              "event": { "id": 1234567890123456789, "tags": [ { "id": 1234567890123456789 } ] }
            }
            """);
    private static final ExpectedValueResolver AS_WRITTEN = expected -> expected;

    private AssertionEvaluator underTest;

    @BeforeEach
    void setUp() {
        underTest = new AssertionEvaluator();
    }

    static Stream<Arguments> presenceCases() {
        return Stream.of(
                Arguments.of("$.requestId", AssertOperator.EXISTS, true),
                Arguments.of("$.gtin", AssertOperator.EXISTS, true),
                Arguments.of("$.asin", AssertOperator.EXISTS, false),
                Arguments.of("$.requestId", AssertOperator.ABSENT, false),
                Arguments.of("$.gtin", AssertOperator.ABSENT, false),
                Arguments.of("$.asin", AssertOperator.ABSENT, true),
                Arguments.of("$.requestId", AssertOperator.IS_NULL, false),
                Arguments.of("$.gtin", AssertOperator.IS_NULL, true),
                Arguments.of("$.asin", AssertOperator.IS_NULL, true),
                Arguments.of("$.requestId", AssertOperator.NOT_NULL, true),
                Arguments.of("$.gtin", AssertOperator.NOT_NULL, false),
                Arguments.of("$.asin", AssertOperator.NOT_NULL, false),
                Arguments.of("$.ads[?(@.qt == 'boots')]", AssertOperator.ABSENT, true),
                Arguments.of("$.ads[?(@.qt == 'shoes')]", AssertOperator.EXISTS, true),
                Arguments.of("$.ads[*].asin", AssertOperator.EXISTS, true),
                Arguments.of("$.ads[*].gtin", AssertOperator.EXISTS, false),
                Arguments.of("$.ads[*].gtin", AssertOperator.ABSENT, true),
                Arguments.of("$.ads[*].asin", AssertOperator.ABSENT, false),
                Arguments.of("$.ads[*].title", AssertOperator.NOT_NULL, true),
                Arguments.of("$.ads[*].gtin", AssertOperator.NOT_NULL, false),
                Arguments.of("$.ads[*].gtin", AssertOperator.IS_NULL, false),
                Arguments.of("$.ads[*].title", AssertOperator.IS_NULL, false),
                Arguments.of("$.adz[*].asin", AssertOperator.EXISTS, false),
                Arguments.of("$.adz[*].asin", AssertOperator.ABSENT, false),
                Arguments.of("$.adz[*].asin", AssertOperator.IS_NULL, false),
                Arguments.of("$.adz[*].asin", AssertOperator.NOT_NULL, false));
    }

    @ParameterizedTest(name = "{0} {1} passes: {2}")
    @MethodSource("presenceCases")
    void evaluate_whenPresenceOperatorMeetsValueNullOrMissingKey_shouldFollowTheSpecSemantics(
            String path, AssertOperator operator, boolean passes) {
        List<AssertionFailedError> result = underTest.evaluate(
                List.of(assertion(path, Map.of(operator, BooleanNode.TRUE))), DOCUMENT, AS_WRITTEN, WHERE);

        assertThat(result.isEmpty()).isEqualTo(passes);
    }

    static Stream<Arguments> operatorCases() {
        return Stream.of(
                Arguments.of("$.requestId", AssertOperator.EQUALS, "\"req-42\"", "\"req-41\"",
                        "expected \"req-41\" but was \"req-42\""),
                Arguments.of("$.ads[2].price", AssertOperator.EQUALS, "15.0", "16",
                        "expected 16 but was 15"),
                Arguments.of("$.ads[0]", AssertOperator.EQUALS, "{\"qt\": \"shoes\"}", "{\"qt\": \"boots\"}",
                        "qt"),
                Arguments.of("$.requestId", AssertOperator.NOT_EQUALS, "\"req-41\"", "\"req-42\"",
                        "expected a value other than \"req-42\""),
                Arguments.of("$.ads[?(@.qt == 'shoes')]", AssertOperator.SIZE, "2", "3",
                        "expected size 3 but was 2"),
                Arguments.of("$.ads[0].price", AssertOperator.GTE, "120", "121",
                        "expected a number >= 121 but was 120"),
                Arguments.of("$.ads[1].price", AssertOperator.LTE, "80", "79",
                        "expected a number <= 79 but was 80"),
                Arguments.of("$.ads[*].qt", AssertOperator.CONTAINS, "\"socks\"", "\"boots\"",
                        "expected an array containing [\"boots\"]"),
                Arguments.of("$.ads[0].title", AssertOperator.CONTAINS, "\"shoe\"", "\"boot\"",
                        "expected a string containing \"boot\""),
                Arguments.of("$.ads[0].title", AssertOperator.CONTAINS, "[\"Red\", \"shoe\"]", "[\"Red\", \"boot\"]",
                        "expected a string containing [\"boot\"] but was \"Red shoes\""),
                Arguments.of("$.ads[0].title", AssertOperator.CONTAINS, null, "{\"title\": \"Red\"}",
                        "the actual value is a string, so `contains` needs a string or a list of strings, but got"
                                + " {\"title\":\"Red\"}"),
                Arguments.of("$.ads[0].title", AssertOperator.CONTAINS, null, "[\"Red\", 1]",
                        "needs a string or a list of strings, but got [\"Red\",1]"),
                Arguments.of("$.event", AssertOperator.EQUALS, "{\"id\": 1234567890123456789}",
                        "{\"id\": 1234567890123456788}",
                        "$.id: expected 1234567890123456788 but was 1234567890123456789"),
                Arguments.of("$.event.tags", AssertOperator.EQUALS, "[{\"id\": 1234567890123456789}]",
                        "[{\"id\": 1234567890123456788}]",
                        "$[0].id: expected 1234567890123456788 but was 1234567890123456789"),
                Arguments.of("$.event", AssertOperator.NOT_EQUALS, "{\"id\": 1234567890123456788}",
                        "{\"id\": 1234567890123456789}", "expected a value other than {\"id\":1234567890123456789}"),
                Arguments.of("$.ads[*].title", AssertOperator.CONTAINS_EXACTLY,
                        "[\"Red shoes\", \"Blue shoes\", \"Socks\"]", "[\"Blue shoes\", \"Red shoes\", \"Socks\"]",
                        "in this order but was [\"Red shoes\",\"Blue shoes\",\"Socks\"]"),
                Arguments.of("$.ads[*].section", AssertOperator.CONTAINS_IN_ANY_ORDER,
                        "[\"side\", \"top\", \"top\"]", "[\"side\", \"top\"]", "unexpected [\"top\"]"),
                Arguments.of("$.ads[*].title", AssertOperator.UNIQUE, "true", null,
                        null),
                Arguments.of("$.ads[*].section", AssertOperator.UNIQUE, null, "true",
                        "found duplicates [\"top\"]"),
                Arguments.of("$.requestId", AssertOperator.STARTS_WITH, "\"req-\"", "\"res-\"",
                        "expected a string starting with \"res-\" but was \"req-42\""),
                Arguments.of("$.ads[2].asin", AssertOperator.MATCHES, "\"B0+[A-Z]+\"", "\"X.*\"",
                        "expected a string matching /X.*/ but was \"B00SOCKS\""));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("operatorCases")
    void evaluate_whenOperatorHoldsOrNot_shouldPassOrReportPathOperatorExpectedAndActual(
            String path, AssertOperator operator, String passing, String failing, String message) {
        if (passing != null) {
            assertThat(underTest.evaluate(List.of(assertion(path, Map.of(operator, json(passing)))), DOCUMENT,
                    AS_WRITTEN, WHERE)).isEmpty();
        }
        if (failing != null) {
            List<AssertionFailedError> result = underTest.evaluate(
                    List.of(assertion(path, Map.of(operator, json(failing)))), DOCUMENT, AS_WRITTEN, WHERE);

            assertThat(result).singleElement().extracting(Throwable::getMessage).asString()
                    .startsWith(WHERE + ": assert `" + path + "` " + operator.yamlName() + ": ")
                    .contains(message);
        }
    }

    @Test
    void evaluate_whenKeyIsPresentWithNull_shouldReportAbsentFailureAsPresentNull() {
        List<AssertionFailedError> result = underTest.evaluate(
                List.of(assertion("$.gtin", Map.of(AssertOperator.ABSENT, BooleanNode.TRUE))), DOCUMENT, AS_WRITTEN,
                WHERE);

        assertThat(result).singleElement().extracting(Throwable::getMessage).asString()
                .endsWith("absent: expected the key to be absent, but it is present with value null");
    }

    @Test
    void evaluate_whenObjectComparisonIsStrict_shouldRejectExtraActualFields() {
        Assertion lenient = assertion("$.ads[0]", Map.of(AssertOperator.EQUALS, json("{\"qt\": \"shoes\"}")));
        Assertion strict = new Assertion("$.ads[0]", false, BodyMode.STRICT,
                Map.of(AssertOperator.EQUALS, json("{\"qt\": \"shoes\"}")), null, null);

        assertThat(underTest.evaluate(List.of(lenient), DOCUMENT, AS_WRITTEN, WHERE)).isEmpty();
        assertThat(underTest.evaluate(List.of(strict), DOCUMENT, AS_WRITTEN, WHERE)).singleElement()
                .extracting(Throwable::getMessage).asString().contains("$.price: unexpected field with value 120");
    }

    @Test
    void evaluate_whenAllAndNoneQuantifyOverElements_shouldNameTheOffendingElement() {
        Assertion priced = assertion("$.price", Map.of(AssertOperator.GTE, json("20")));
        Assertion withAsin = assertion("$.asin", Map.of(AssertOperator.EXISTS, BooleanNode.TRUE));
        Assertion all = new Assertion("$.ads", false, null, Map.of(), priced, null);
        Assertion none = new Assertion("$.ads", false, null, Map.of(), null, withAsin);
        Assertion noneOfShoes = new Assertion("$.ads[?(@.qt == 'shoes')]", false, null, Map.of(), null, withAsin);

        List<AssertionFailedError> result = underTest.evaluate(List.of(all, none, noneOfShoes), DOCUMENT, AS_WRITTEN,
                WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": assert `$.ads` all: element [2] {\"title\":\"Socks\",\"qt\":\"socks\",\"section\":\"top\","
                        + "\"price\":15,\"asin\":\"B00SOCKS\"} fails `$.price` gte: expected a number >= 20 but was 15",
                WHERE + ": assert `$.ads` none: element [2] {\"title\":\"Socks\",\"qt\":\"socks\",\"section\":\"top\","
                        + "\"price\":15,\"asin\":\"B00SOCKS\"} satisfies `$.asin` [exists]");
    }

    @Test
    void evaluate_whenParseJsonIsSet_shouldParseDefiniteAndFilteredStrings() {
        Assertion definite = new Assertion("$.diag[0].payload", true, null,
                Map.of(AssertOperator.EQUALS, json("{\"dropped\": [\"ad-9\"]}")), null, null);
        Assertion filtered = new Assertion("$.diag[?(@.step == 'dedup')].payload", true, null, Map.of(),
                assertion("$.dropped", Map.of(AssertOperator.SIZE, json("2"))), null);

        List<AssertionFailedError> result = underTest.evaluate(List.of(definite, filtered), DOCUMENT, AS_WRITTEN,
                WHERE);

        assertThat(result).singleElement().extracting(Throwable::getMessage).asString()
                .contains("all: element [0] {\"dropped\":[\"ad-9\"]} fails `$.dropped` size: expected size 2 but"
                        + " was 1");
    }

    @Test
    void evaluate_whenParseJsonMeetsANonString_shouldReportIt() {
        Assertion parsed = new Assertion("$.ads[0].price", true, null,
                Map.of(AssertOperator.EQUALS, json("120")), null, null);

        List<AssertionFailedError> result = underTest.evaluate(List.of(parsed), DOCUMENT, AS_WRITTEN, WHERE);

        assertThat(result).singleElement().extracting(Throwable::getMessage).asString()
                .contains("parseJson: expected a JSON string but was 120");
    }

    @Test
    void evaluate_whenValueOperatorMeetsAMissingKey_shouldSayTheKeyIsMissing() {
        List<AssertionFailedError> result = underTest.evaluate(
                List.of(assertion("$.nope", Map.of(AssertOperator.EQUALS, json("1")))), DOCUMENT, AS_WRITTEN, WHERE);

        assertThat(result).singleElement().extracting(Throwable::getMessage).asString()
                .endsWith("`$.nope` equals: the key is missing");
    }

    @Test
    void evaluate_whenTheDefinitePrefixOfAnIndefinitePathIsMissing_shouldFailEveryOperator() {
        Assertion misspelled = assertion("$.adz[*].qt", Map.of(AssertOperator.SIZE, json("0"),
                AssertOperator.ABSENT, BooleanNode.TRUE, AssertOperator.CONTAINS, json("\"shoes\"")));
        Assertion quantified = new Assertion("$.adz[?(@.qt == 'shoes')]", false, null, Map.of(),
                assertion("$.price", Map.of(AssertOperator.GTE, json("1"))), null);

        List<AssertionFailedError> result = underTest.evaluate(List.of(misspelled, quantified), DOCUMENT, AS_WRITTEN,
                WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": assert `$.adz[*].qt` absent: path prefix `$['adz']` missing",
                WHERE + ": assert `$.adz[*].qt` size: path prefix `$['adz']` missing",
                WHERE + ": assert `$.adz[*].qt` contains: path prefix `$['adz']` missing",
                WHERE + ": assert `$.adz[?(@.qt == 'shoes')]` all: path prefix `$['adz']` missing");
    }

    @Test
    void evaluate_whenAnIndefinitePathHasNoMatches_shouldCountZeroAndFailNullChecks() {
        Assertion none = assertion("$.ads[?(@.qt == 'boots')]", Map.of(AssertOperator.SIZE, json("0")));
        Assertion nulls = assertion("$.ads[?(@.qt == 'boots')].title", Map.of(AssertOperator.IS_NULL,
                BooleanNode.TRUE));

        List<AssertionFailedError> result = underTest.evaluate(List.of(none, nulls), DOCUMENT, AS_WRITTEN, WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": assert `$.ads[?(@.qt == 'boots')].title` isNull: expected at least one match, all null, but"
                        + " there is none");
    }

    @Test
    void evaluate_whenExpectedValueIsAReference_shouldCompareWithTheResolvedValue() {
        JsonNode reference = json("{\"ref\": \"isp\", \"path\": \"$.requestId\"}");
        ExpectedValueResolver resolver = expected -> expected.equals(reference) ? json("\"req-42\"") : expected;

        List<AssertionFailedError> result = underTest.evaluate(
                List.of(assertion("$.requestId", Map.of(AssertOperator.EQUALS, reference))), DOCUMENT, resolver, WHERE);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_whenAReferenceCantBeRead_shouldFailThatOperatorAndStillCheckTheOthers() {
        JsonNode reference = json("{\"ref\": \"dsp\", \"path\": \"$[0].id\"}");
        ExpectedValueResolver resolver = expected -> {
            if (expected.equals(reference)) {
                throw new AssertionFailedError("cases/a.yaml, case 'ads': reference 'dsp': path `$[0].id`: the key is"
                        + " missing in []");
            }
            return expected;
        };

        List<AssertionFailedError> result = underTest.evaluate(List.of(assertion("$.requestId",
                Map.of(AssertOperator.EQUALS, reference, AssertOperator.STARTS_WITH, json("\"x\"")))), DOCUMENT,
                resolver, WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": assert `$.requestId` equals: cases/a.yaml, case 'ads': reference 'dsp': path `$[0].id`: the"
                        + " key is missing in []",
                WHERE + ": assert `$.requestId` startsWith: expected a string starting with \"x\" but was \"req-42\"");
    }

    private static Assertion assertion(String path, Map<AssertOperator, JsonNode> operators) {
        return new Assertion(path, false, null, operators, null, null);
    }
}
