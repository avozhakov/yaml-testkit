package com.yamltestkit.domain.assertion;

import com.yamltestkit.domain.model.TextExpectation;
import com.yamltestkit.domain.model.TextOperator;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * {@code expect.bodyText}: string matchers on the raw body; a matcher with a list of values must hold for each. The
 * message quotes the body shortened; the failure's actual value keeps it whole for the IDE diff.
 */
public final class TextAssertion {

    public List<AssertionFailedError> check(TextExpectation text, String actual, String where) {
        List<AssertionFailedError> failures = new ArrayList<>();
        for (TextOperator operator : TextOperator.values()) {
            for (String value : text.matchers().getOrDefault(operator, List.of())) {
                if (!holds(operator, value, actual)) {
                    failures.add(new AssertionFailedError(where + ": bodyText " + operator.yamlName() + " "
                            + quote(value) + " does not hold for body " + quote(abbreviate(actual)), value, actual));
                }
            }
        }
        return failures;
    }

    private static boolean holds(TextOperator operator, String value, String actual) {
        return switch (operator) {
            case EQUALS -> actual.equals(value);
            case CONTAINS -> actual.contains(value);
            case NOT_CONTAINS -> !actual.contains(value);
            case STARTS_WITH -> actual.startsWith(value);
            case MATCHES -> Pattern.compile(value).matcher(actual).matches();
        };
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
