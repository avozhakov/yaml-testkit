package com.yamltestkit.domain.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.assertion.JsonPaths.PathResult;
import com.yamltestkit.domain.model.AssertOperator;
import com.yamltestkit.domain.model.Assertion;
import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.List;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * Evaluates {@code assert} entries on a JSON document: the path is read with {@link JsonPaths}, {@code parseJson}
 * parses the selected string(s), then every operator, {@code all} and {@code none} are checked. All failures of an
 * entry are reported, each naming the step, path, operator, expected and actual value.
 */
public final class AssertionEvaluator {

    /**
     * @param where the case file, case and step, prefixed to every failure message
     */
    public List<AssertionFailedError> evaluate(List<Assertion> assertions, JsonNode root,
                                               ExpectedValueResolver expected, String where) {
        List<AssertionFailedError> failures = new ArrayList<>();
        for (Assertion assertion : assertions) {
            failures(assertion, root, expected).forEach(failure -> failures.add(
                    new AssertionFailedError(where + ": assert " + failure)));
        }
        return failures;
    }

    private List<String> failures(Assertion assertion, JsonNode root, ExpectedValueResolver expected) {
        String prefix = "`" + assertion.path() + "` ";
        PathResult actual = JsonPaths.read(root, assertion.path());
        if (assertion.parseJson() && actual.found()) {
            try {
                actual = actual.withValue(JsonTexts.parse(actual.value()));
            } catch (IllegalArgumentException e) {
                return List.of(prefix + "parseJson: " + e.getMessage());
            }
        }
        List<String> failures = new ArrayList<>();
        for (AssertOperator operator : AssertOperator.values()) {
            JsonNode value = assertion.operators().get(operator);
            String failure = value == null ? null : check(operator, value, actual, assertion, expected);
            if (failure != null) {
                failures.add(prefix + operator.yamlName() + ": " + failure);
            }
        }
        if (assertion.all() != null) {
            quantified(assertion.all(), actual, expected, true).forEach(failure -> failures.add(prefix + failure));
        }
        if (assertion.none() != null) {
            quantified(assertion.none(), actual, expected, false).forEach(failure -> failures.add(prefix + failure));
        }
        return failures;
    }

    /** A {@code { ref }} that can't be read fails this operator only, so the entry's other failures still show. */
    private static @Nullable String check(AssertOperator operator, JsonNode value, PathResult actual,
                                          Assertion assertion, ExpectedValueResolver expected) {
        JsonNode resolved;
        try {
            resolved = expected.resolve(value);
        } catch (AssertionFailedError e) {
            return e.getMessage();
        }
        return OperatorCheck.check(operator, resolved, actual, assertion.mode());
    }

    /** {@code all}: every element satisfies the nested assertion; {@code none}: no element does. */
    private List<String> quantified(Assertion nested, PathResult actual, ExpectedValueResolver expected,
                                    boolean all) {
        String name = all ? "all" : "none";
        if (!actual.found()) {
            return List.of(name + ": " + actual.missingReason());
        }
        if (!actual.value().isArray()) {
            return List.of(name + ": expected an array but was " + abbreviate(actual.value()));
        }
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < actual.value().size(); i++) {
            JsonNode element = actual.value().get(i);
            List<String> elementFailures = failures(nested, element, expected);
            if (all && !elementFailures.isEmpty()) {
                failures.add(name + ": element [" + i + "] " + abbreviate(element) + " fails "
                        + String.join("; ", elementFailures));
            } else if (!all && elementFailures.isEmpty()) {
                failures.add(name + ": element [" + i + "] " + abbreviate(element) + " satisfies `"
                        + nested.path() + "` " + nested.operators().keySet().stream()
                        .map(AssertOperator::yamlName).toList());
            }
        }
        return failures;
    }
}
