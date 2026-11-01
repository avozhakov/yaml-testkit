package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.model.BodyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

class JsonComparatorTest {

    private static final JsonNode ID_789 = json("{\"id\": 1234567890123456789, \"name\": \"a\"}");
    private static final JsonNode ID_788 = json("{\"id\": 1234567890123456788, \"name\": \"a\"}");
    private static final JsonNode IDS_789 = json("[1, 1234567890123456789]");
    private static final JsonNode IDS_788 = json("[1, 1234567890123456788]");

    @ParameterizedTest
    @EnumSource(value = BodyMode.class, names = {"LENIENT", "STRICT"})
    void difference_when19DigitNumbersInAnObjectDifferInTheLastDigit_shouldReportThem(BodyMode mode) {
        Optional<String> result = JsonComparator.difference(ID_789, ID_788, mode);

        assertThat(result).contains("$.id: expected 1234567890123456789 but was 1234567890123456788");
    }

    @ParameterizedTest
    @EnumSource(value = BodyMode.class, names = {"LENIENT", "STRICT"})
    void difference_when19DigitNumbersInAnArrayDifferInTheLastDigit_shouldReportThem(BodyMode mode) {
        Optional<String> result = JsonComparator.difference(IDS_789, IDS_788, mode);

        assertThat(result).contains("$[1]: expected 1234567890123456789 but was 1234567890123456788");
    }

    @Test
    void difference_whenNumbersAreEqualInValueButNotInType_shouldMatch() {
        Optional<String> result = JsonComparator.difference(json("{\"price\": 15}"), json("{\"price\": 15.0}"),
                BodyMode.STRICT);

        assertThat(result).isEmpty();
    }

    @Test
    void difference_whenActualHasExtraFields_shouldMatchLenientlyButNotStrictly() {
        JsonNode expected = json("{\"ads\": [{\"id\": 1}]}");
        JsonNode actual = json("{\"ads\": [{\"id\": 1, \"rank\": 2}], \"requestId\": \"r\"}");

        assertThat(JsonComparator.difference(expected, actual, BodyMode.LENIENT)).isEmpty();
        assertThat(JsonComparator.difference(expected, actual, BodyMode.STRICT)).contains(
                "$.ads[0].rank: unexpected field with value 2; $.requestId: unexpected field with value \"r\"");
    }

    @Test
    void difference_whenAFieldIsMissingOrAnArrayHasAnotherLength_shouldNameThePaths() {
        JsonNode expected = json("{\"id\": 1, \"ads\": [1, 2]}");
        JsonNode actual = json("{\"ads\": [1]}");

        Optional<String> result = JsonComparator.difference(expected, actual, BodyMode.LENIENT);

        assertThat(result).contains("$.id: missing, expected 1; $.ads: expected 2 elements but was 1: [1]");
    }

    @Test
    void difference_whenScalarsDifferAtTheRoot_shouldReportWithoutAPath() {
        Optional<String> result = JsonComparator.difference(json("5"), json("4"), BodyMode.LENIENT);

        assertThat(result).contains("expected 5 but was 4");
    }
}
