package com.yamltestkit.convert;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Converts between YAML values and Java objects of the types it {@link #supports}. Converters form a chain (see
 * {@link ValueConverters}): the first converter that supports a type handles it, and nested types are converted
 * back through the chain via the {@link ConversionContext}.
 *
 * <p>An implementation registered under {@code converters} in {@code _context.yaml} or on {@code @YamlCases} needs
 * a public no-argument constructor; it is tried before the built-in converters.
 */
public interface ValueConverter {

    /** True when this converter handles {@code type}: a target type, or the runtime class of a value. */
    boolean supports(Type type);

    /**
     * @param value YAML value, a {@code NullNode} for YAML {@code null}
     */
    @Nullable Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx);

    /**
     * @param value never {@code null}; the chain writes {@code null} itself
     */
    JsonNode toJson(Object value, ConversionContext ctx);

    /**
     * The type of value {@link #wrap} accepts for {@code targetType} (e.g. {@code T} of {@code Optional<T>}), so a
     * {@code returnsArg} stub is checked while stubbing; empty when this converter doesn't wrap.
     */
    default Optional<Type> wrappedType(Type targetType) {
        return Optional.empty();
    }

    /** Wraps a value into {@code targetType} (e.g. {@code Optional.of(value)}), for {@code returnsArg}. */
    default Object wrap(Object value, Type targetType, ConversionContext ctx) {
        throw new UnsupportedOperationException(getClass().getName() + " can't wrap into " + targetType.getTypeName());
    }
}
