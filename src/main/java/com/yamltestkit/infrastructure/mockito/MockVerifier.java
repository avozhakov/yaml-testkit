package com.yamltestkit.infrastructure.mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.yamltestkit.convert.ConversionException;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.CallCounts;
import com.yamltestkit.domain.assertion.CheckResult;
import com.yamltestkit.domain.assertion.ExpectedValueResolver;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.CallCount;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.VerifyCheck;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import com.yamltestkit.infrastructure.reflect.MethodResolver;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * A {@code verify} check on a mock or spy (design D8): reads the calls Mockito recorded on it, keeps those of the
 * check's method (resolved like a stub's, {@code paramTypes} choosing an overload) whose arguments match {@code args}
 * (same grammar as stubs; a {@code { ref }} literal is resolved first) and {@code argType}, then checks their number.
 * {@code noInteractions} counts every call of the mock. The kept calls become {@code [ { "args": [ … ] } ]} through
 * the value converters, in call order, for the nested {@code assert} entries and the {@code as} capture.
 */
@RequiredArgsConstructor
public final class MockVerifier {

    private static final Predicate<Invocation> EVERY_CALL = invocation -> true;

    private final ValueConverters converters;
    private final ArgMatcherFactory argMatchers;
    private final AssertionEvaluator assertions;

    public static MockVerifier create(ValueConverters converters) {
        return new MockVerifier(converters, new ArgMatcherFactory(converters), new AssertionEvaluator());
    }

    /**
     * @param declaredType the type the mock stands for, generics included, to resolve the method and its arguments
     * @param errors       where an {@code args} literal records an actual argument it can't convert to JSON; drained
     *                     into the check's failures
     * @param where        the step and check, prefixed to failure messages
     * @throws CaseDefinitionException when the method, an {@code args} literal or the {@code argType} is invalid
     */
    public CheckResult verify(VerifyCheck.OnMock check, Object mock, Type declaredType,
                              ExpectedValueResolver expected, StubErrors errors, String where) {
        Predicate<Invocation> filter = EVERY_CALL;
        if (!(check.count() instanceof CallCount.NoInteractions)) {
            List<ArgMatcher> args;
            try {
                args = resolved(check.args(), expected);
            } catch (AssertionFailedError unreadable) {
                return new CheckResult(List.of(new AssertionFailedError(where + ": args: " + unreadable.getMessage(),
                        unreadable)), null);
            }
            filter = filter(check, args, declaredType, errors, where);
        }
        List<Invocation> all = List.copyOf(Mockito.mockingDetails(mock).getInvocations());
        List<Invocation> matching = all.stream().filter(filter).toList();
        List<AssertionFailedError> failures = new ArrayList<>(errors.drain());
        CallCounts.mismatch(check.count(), matching.size()).ifPresent(mismatch -> failures.add(
                new AssertionFailedError(where + ": " + mismatch + "; calls of the mock: " + describe(all))));
        if (check.as() == null && check.asserts().isEmpty()) {
            return new CheckResult(failures, null);
        }
        JsonNode captured;
        try {
            captured = captured(matching);
        } catch (ConversionException e) {
            failures.add(new AssertionFailedError(where + ": a captured call can't be converted to JSON: "
                    + e.getMessage(), e));
            return new CheckResult(failures, null);
        }
        failures.addAll(assertions.evaluate(check.asserts(), captured, expected, where));
        return new CheckResult(failures, captured);
    }

    private Predicate<Invocation> filter(VerifyCheck.OnMock check, @Nullable List<ArgMatcher> args, Type declaredType,
                                         StubErrors matchErrors, String where) {
        Method method = MethodResolver.resolve(GenericTypes.rawClass(declaredType), check.method(), where);
        List<ArgumentMatcher<Object>> matchers = argMatchers.forMethod(args, method, declaredType, matchErrors,
                where);
        Predicate<Invocation> filter = invocation -> sameMethod(invocation.getMethod(), method)
                && argumentsMatch(matchers, invocation.getRawArguments());
        return check.argType() == null ? filter : filter.and(argType(check.argType(), method, where));
    }

    /**
     * {@code { ref }} literals replaced by the values they point to, before they are converted to matchers.
     *
     * @throws AssertionFailedError when a reference can't be read; it fails this check only (design D19)
     */
    private static @Nullable List<ArgMatcher> resolved(@Nullable List<ArgMatcher> args,
                                                      ExpectedValueResolver expected) {
        return args == null ? null : args.stream()
                .map(arg -> arg instanceof ArgMatcher.Literal literal
                        ? new ArgMatcher.Literal(expected.resolve(literal.value()))
                        : arg)
                .toList();
    }

    private static Predicate<Invocation> argType(VerifyCheck.ArgTypeFilter argType, Method method, String where) {
        if (argType.index() >= method.getParameterCount()) {
            throw new CaseDefinitionException(where + ": argType.index " + argType.index() + " is out of range; "
                    + TypeNames.signature(method) + " takes " + method.getParameterCount() + " arguments");
        }
        Class<?> type = TypeNames.load(argType.type(), where + ": argType");
        return invocation -> type.isInstance(invocation.getRawArguments()[argType.index()]);
    }

    /** By name and parameter types: the recorded method may be declared by a supertype of the resolved one. */
    private static boolean sameMethod(Method recorded, Method expected) {
        return recorded.getName().equals(expected.getName())
                && Arrays.equals(recorded.getParameterTypes(), expected.getParameterTypes());
    }

    private static boolean argumentsMatch(List<ArgumentMatcher<Object>> matchers, Object[] arguments) {
        if (matchers.size() != arguments.length) {
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (!matchers.get(i).matches(arguments[i])) {
                return false;
            }
        }
        return true;
    }

    private JsonNode captured(List<Invocation> invocations) {
        ArrayNode calls = JsonNodeFactory.instance.arrayNode();
        for (Invocation invocation : invocations) {
            ArrayNode args = calls.addObject().putArray("args");
            for (Object argument : invocation.getRawArguments()) {
                args.add(converters.toJson(argument));
            }
        }
        return calls;
    }

    private static String describe(List<Invocation> invocations) {
        if (invocations.isEmpty()) {
            return "none";
        }
        return abbreviate(invocations.stream()
                .map(invocation -> invocation.getMethod().getName() + Arrays.toString(invocation.getRawArguments()))
                .collect(Collectors.joining(", ", "[", "]")));
    }
}
