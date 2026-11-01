package com.yamltestkit.infrastructure.mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.convert.ConversionException;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.ThrowSpec;
import com.yamltestkit.infrastructure.reflect.Constructors;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the exception of a {@code throws} spec with the public constructor whose parameters fit the YAML
 * {@code args} (or the {@code message}). A {@code cause} is built the same way and passed to a constructor with a
 * trailing {@code Throwable} parameter, or set with {@code initCause} when no such constructor fits.
 */
@RequiredArgsConstructor
public final class ExceptionFactory {

    private final ValueConverters converters;

    /**
     * @param where how the YAML location is named in errors
     * @throws CaseDefinitionException when the type is not an exception or no constructor fits; the message lists
     *                                 the type's constructors
     */
    public Throwable create(ThrowSpec spec, String where) {
        Class<?> type = TypeNames.load(spec.type(), where);
        if (!Throwable.class.isAssignableFrom(type)) {
            throw new CaseDefinitionException(where + ": " + type.getName() + " is not a Throwable");
        }
        if (spec.message() != null && !spec.args().isEmpty()) {
            throw new CaseDefinitionException(where + ": throws has both `message` and `args`; give one of them");
        }
        Throwable cause = spec.cause() == null ? null : create(spec.cause(), where + ", cause");
        List<JsonNode> args = spec.message() == null ? spec.args() : List.of(TextNode.valueOf(spec.message()));
        Throwable created = cause == null ? null : construct(type, args, cause, where);
        if (created != null) {
            return created;
        }
        created = construct(type, args, null, where);
        if (created == null) {
            throw new CaseDefinitionException(where + ": no public constructor of " + type.getName() + " fits "
                    + args + (cause == null ? "" : " (with or without a trailing cause)") + "; constructors: "
                    + constructors(type));
        }
        if (cause != null) {
            created.initCause(cause);
        }
        return created;
    }

    private @Nullable Throwable construct(Class<?> type, List<JsonNode> args, @Nullable Throwable cause,
                                          String where) {
        int arity = args.size() + (cause == null ? 0 : 1);
        List<Constructor<?>> fitting = new ArrayList<>();
        List<Object[]> values = new ArrayList<>();
        for (Constructor<?> constructor : type.getConstructors()) {
            Object[] converted = constructor.getParameterCount() == arity ? convert(constructor, args, cause, where)
                    : null;
            if (converted != null) {
                fitting.add(constructor);
                values.add(converted);
            }
        }
        if (fitting.size() > 1) {
            throw new CaseDefinitionException(where + ": several constructors of " + type.getName() + " fit " + args
                    + ": " + fitting.stream().map(TypeNames::signature).sorted().collect(Collectors.joining(", ")));
        }
        return fitting.isEmpty() ? null
                : (Throwable) Constructors.instantiate(fitting.getFirst(), values.getFirst(), where);
    }

    /** The constructor arguments converted from YAML, or {@code null} when they don't fit its parameters. */
    private Object @Nullable [] convert(Constructor<?> constructor, List<JsonNode> args, @Nullable Throwable cause,
                                        String where) {
        Class<?>[] parameters = constructor.getParameterTypes();
        Object[] values = new Object[parameters.length];
        for (int i = 0; i < args.size(); i++) {
            if (!fits(args.get(i), parameters[i])) {
                return null;
            }
            try {
                values[i] = converters.fromYaml(args.get(i), constructor.getGenericParameterTypes()[i],
                        where + ", throws.args[" + i + "]");
            } catch (ConversionException e) {
                // Not a failure: this constructor just doesn't fit; the caller reports when none does.
                return null;
            }
        }
        if (cause != null) {
            if (!parameters[args.size()].isInstance(cause)) {
                return null;
            }
            values[args.size()] = cause;
        }
        return values;
    }

    /** Whether a YAML value's kind can be a parameter of this type, so {@code 500} never becomes a String. */
    private static boolean fits(JsonNode value, Class<?> parameter) {
        Class<?> boxed = ClassUtils.resolvePrimitiveIfNecessary(parameter);
        if (boxed == Object.class) {
            return true;
        }
        if (value.isNull()) {
            return !parameter.isPrimitive();
        }
        if (value.isNumber()) {
            return Number.class.isAssignableFrom(boxed);
        }
        if (value.isTextual()) {
            return CharSequence.class.isAssignableFrom(boxed) || boxed.isEnum() || boxed == Character.class;
        }
        if (value.isBoolean()) {
            return boxed == Boolean.class;
        }
        return !Number.class.isAssignableFrom(boxed) && !CharSequence.class.isAssignableFrom(boxed)
                && boxed != Boolean.class && boxed != Character.class;
    }

    private static String constructors(Class<?> type) {
        return Arrays.stream(type.getConstructors()).map(TypeNames::signature).sorted()
                .collect(Collectors.joining(", "));
    }
}
