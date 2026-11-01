package com.yamltestkit.infrastructure.mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.convert.ConversionException;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.assertion.JsonEquality;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mockito.ArgumentMatcher;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the YAML {@code args} matchers of a stub (later also of a verification) into Mockito matchers: a literal
 * matches by exact JSON equality after conversion to the parameter type (converted once, while stubbing),
 * {@code "#any"} matches anything ({@code null} included), {@code { anyOf: <FQN> }} matches instances of the type.
 * No {@code args} matches any arguments.
 */
@RequiredArgsConstructor
public final class ArgMatcherFactory {

    private final ValueConverters converters;

    /**
     * @param declaredType the type the mock stands for, to resolve generic parameter types
     * @param errors       where a literal matcher records an actual argument it can't convert to JSON; it then
     *                     doesn't match, and the error is reported after the call
     * @throws CaseDefinitionException when the number of matchers differs from the number of parameters
     */
    public List<ArgumentMatcher<Object>> forMethod(@Nullable List<ArgMatcher> args, Method method, Type declaredType,
                                                   StubErrors errors, String where) {
        int count = method.getParameterCount();
        if (args == null) {
            return Collections.nCopies(count, new AnyArgument());
        }
        if (args.size() != count) {
            throw new CaseDefinitionException(where + ": `args` has " + args.size() + " matchers but "
                    + TypeNames.signature(method) + " takes " + count + " arguments");
        }
        List<ArgumentMatcher<Object>> matchers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Type parameterType = GenericTypes.resolve(method.getGenericParameterTypes()[i], declaredType);
            matchers.add(matcher(args.get(i), parameterType, errors, where + ", args[" + i + "]"));
        }
        return matchers;
    }

    private ArgumentMatcher<Object> matcher(ArgMatcher arg, Type parameterType, StubErrors errors, String where) {
        return switch (arg) {
            case ArgMatcher.Any ignored -> new AnyArgument();
            case ArgMatcher.OfType ofType -> new InstanceOf(TypeNames.load(ofType.type(), where));
            case ArgMatcher.Literal literal -> new JsonEqual(
                    converters.toJson(converters.fromYaml(literal.value(), parameterType, where)), converters, errors,
                    where);
        };
    }

    private record AnyArgument() implements ArgumentMatcher<Object> {

        @Override
        public boolean matches(Object argument) {
            return true;
        }

        @Override
        public String toString() {
            return "\"#any\"";
        }
    }

    private record InstanceOf(Class<?> type) implements ArgumentMatcher<Object> {

        @Override
        public boolean matches(Object argument) {
            return type.isInstance(argument);
        }

        @Override
        public String toString() {
            return "{ anyOf: " + type.getName() + " }";
        }
    }

    private record JsonEqual(JsonNode expected, ValueConverters converters, StubErrors errors, String where)
            implements ArgumentMatcher<Object> {

        @Override
        public boolean matches(Object argument) {
            JsonNode actual;
            try {
                actual = converters.toJson(argument);
            } catch (ConversionException e) {
                // Not swallowed: recorded and reported after the call, so the code under test never sees it.
                errors.record(where + ": the actual argument can't be compared with the stub's value: "
                        + e.getMessage(), e);
                return false;
            }
            return JsonEquality.equal(expected, actual);
        }

        @Override
        public String toString() {
            return expected.toString();
        }
    }
}
