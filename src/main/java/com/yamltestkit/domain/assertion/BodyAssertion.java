package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.json.JsonMappers;
import com.yamltestkit.domain.model.BodyExpectation;
import com.yamltestkit.domain.model.BodyMode;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Locale;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * {@code expect.body}: {@link BodyMode#EXACT} compares the raw text; otherwise the actual body is parsed as JSON,
 * the {@code ignore} paths are removed from both sides and the trees are compared by {@link JsonComparator}, whose
 * message lists the differing fields.
 */
public final class BodyAssertion {

    /**
     * @param expected the expected body, its {@code { ref }} already resolved
     * @return the failure, or an empty list when the body matches
     */
    public List<AssertionFailedError> check(BodyExpectation body, JsonNode expected, String actualText, String where) {
        if (body.mode() == BodyMode.EXACT) {
            return expected.asText().equals(actualText) ? List.of()
                    : List.of(new AssertionFailedError(where + ": body (exact) differs", expected.asText(), actualText));
        }
        JsonNode actual;
        try {
            actual = JsonMappers.plain().readTree(actualText);
        } catch (JsonProcessingException e) {
            return List.of(new AssertionFailedError(where + ": body is not JSON (" + e.getOriginalMessage() + "): "
                    + abbreviate(actualText)));
        }
        JsonNode expectedTree = JsonPaths.delete(expected, body.ignore());
        JsonNode actualTree = JsonPaths.delete(actual, body.ignore());
        return JsonComparator.difference(expectedTree, actualTree, body.mode())
                .map(difference -> List.of(new AssertionFailedError(where + ": body ("
                        + body.mode().name().toLowerCase(Locale.ROOT) + ") differs: " + difference,
                        expectedTree.toPrettyString(), actualTree.toPrettyString())))
                .orElse(List.of());
    }
}
