package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.convert.ConversionException;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.ExpectedValueResolver;
import com.yamltestkit.domain.assertion.JsonComparator;
import com.yamltestkit.domain.model.BodyMode;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.ExpectedThrow;
import com.yamltestkit.infrastructure.invocation.CallOutcome;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.RequiredArgsConstructor;
import org.opentest4j.AssertionFailedError;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * Expectations on the outcome of a {@code bean} / {@code class} call: {@code returns} (the returned value's JSON
 * compared like {@code body}, extra actual fields allowed), {@code throws} (type by simple or qualified name of the
 * exception class or a superclass, exact message when given) and {@code assert} over the returned value's JSON. A
 * call that throws while no {@code throws} expects it fails with the step's location and the exception as cause.
 */
@RequiredArgsConstructor
public final class ReturnAssertion {

    private final ValueConverters converters;
    private final AssertionEvaluator assertions;

    /**
     * @return the failed expectations, empty when all hold
     */
    public List<AssertionFailedError> check(Expect expect, CallOutcome outcome, ExpectedValueResolver expected,
                                            String where) {
        return switch (outcome) {
            case CallOutcome.Thrown thrown when expect.throwsException() == null -> List.of(new AssertionFailedError(
                    where + ": call threw " + thrown.exception(), thrown.exception()));
            case CallOutcome.Thrown thrown -> thrownFailures(expect, thrown.exception(), where);
            case CallOutcome.Returned returned -> returnedFailures(expect, returned, expected, where);
        };
    }

    private static List<AssertionFailedError> thrownFailures(Expect expect, Throwable exception, String where) {
        ExpectedThrow expectedThrow = expect.throwsException();
        List<AssertionFailedError> failures = new ArrayList<>();
        if (!TypeNames.matchesHierarchy(expectedThrow.type(), exception.getClass())) {
            failures.add(new AssertionFailedError(where + ": throws: expected " + expectedThrow.type() + " but "
                    + exception.getClass().getName() + " was thrown: " + exception.getMessage(), expectedThrow.type(),
                    exception.getClass().getName(), exception));
        }
        if (expectedThrow.message() != null && !Objects.equals(expectedThrow.message(), exception.getMessage())) {
            failures.add(new AssertionFailedError(where + ": throws: expected message \"" + expectedThrow.message()
                    + "\" but was \"" + exception.getMessage() + "\"", expectedThrow.message(), exception.getMessage(),
                    exception));
        }
        if (!expect.asserts().isEmpty()) {
            failures.add(new AssertionFailedError(where + ": assert needs a returned value, but the call threw "
                    + exception, exception));
        }
        return failures;
    }

    private List<AssertionFailedError> returnedFailures(Expect expect, CallOutcome.Returned returned,
                                                        ExpectedValueResolver expected, String where) {
        JsonNode actual;
        try {
            actual = converters.toJson(returned.value());
        } catch (ConversionException e) {
            return List.of(new AssertionFailedError(where + ": the returned value can't be checked: "
                    + e.getMessage(), e));
        }
        if (expect.throwsException() != null) {
            return List.of(new AssertionFailedError(where + ": throws: expected " + expect.throwsException().type()
                    + " to be thrown, but no exception was thrown (returned " + abbreviate(actual) + ")"));
        }
        List<AssertionFailedError> failures = new ArrayList<>();
        if (!expect.returns().isMissingNode()) {
            returnsFailure(expect.returns(), actual, expected, where).ifPresent(failures::add);
        }
        failures.addAll(assertions.evaluate(expect.asserts(), actual, expected, where));
        return failures;
    }

    /** A reference {@code returns} can't read fails that expectation only, reported with the others. */
    private static Optional<AssertionFailedError> returnsFailure(JsonNode returns, JsonNode actual,
                                                                 ExpectedValueResolver expected, String where) {
        JsonNode expectedValue;
        try {
            expectedValue = expected.resolve(returns);
        } catch (AssertionFailedError unreadableReference) {
            return Optional.of(unreadableReference);
        }
        return JsonComparator.difference(expectedValue, actual, BodyMode.LENIENT).map(difference ->
                new AssertionFailedError(where + ": returns differs: " + difference, expectedValue.toPrettyString(),
                        actual.toPrettyString()));
    }
}
