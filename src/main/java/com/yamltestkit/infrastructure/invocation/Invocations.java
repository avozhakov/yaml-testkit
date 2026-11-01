package com.yamltestkit.infrastructure.invocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

/** Reflective method calls of {@code bean} and {@code class} steps: YAML args converted, the outcome captured. */
public final class Invocations {

    private Invocations() {
    }

    /**
     * @param owner   the type the method is called on, to resolve generic parameter types
     * @param section the YAML section of the call, e.g. {@code call.class}, named in argument locations
     * @throws CaseDefinitionException when the number of {@code args} doesn't match the method's parameters
     */
    public static Object[] arguments(ValueConverters converters, List<JsonNode> args, Method method, Type owner,
                              String where, String section) {
        if (args.size() != method.getParameterCount()) {
            throw new CaseDefinitionException(where + ": " + TypeNames.signature(method) + " takes "
                    + method.getParameterCount() + " arguments, but `args` has " + args.size());
        }
        Object[] values = new Object[args.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = converters.fromYaml(args.get(i), GenericTypes.resolve(method.getGenericParameterTypes()[i],
                    owner), where + ": " + section + ".args[" + i + "]");
        }
        return values;
    }

    /**
     * The step result references read: the returned value's JSON, or, when the call threw, an exception saying so.
     * Computed when first read.
     */
    public static Supplier<JsonNode> result(ValueConverters converters, CallOutcome outcome) {
        return switch (outcome) {
            case CallOutcome.Returned returned -> () -> converters.toJson(returned.value());
            case CallOutcome.Thrown thrown -> () -> {
                throw new IllegalStateException("the call threw " + thrown.exception());
            };
        };
    }

    /** What the method did: its return value, or the exception it threw (unwrapped). */
    public static CallOutcome invoke(Method method, @Nullable Object target, Object[] args)
            throws IllegalAccessException {
        try {
            return CallOutcome.of(method.invoke(target, args));
        } catch (InvocationTargetException e) {
            return new CallOutcome.Thrown(e.getCause());
        }
    }
}
