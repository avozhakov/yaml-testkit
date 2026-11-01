package com.yamltestkit.convert.vavr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yamltestkit.convert.ConversionContext;
import com.yamltestkit.convert.ConversionException;
import com.yamltestkit.convert.ValueConverter;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import io.vavr.control.Validation;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Optional;

/**
 * vavr {@code Validation<E, T>} as {@code { valid: <T> }} / {@code { invalid: <E> }}; {@code returnsArg} wraps into
 * {@code Validation.valid}. Registered by class name only when vavr is on the classpath.
 */
public final class ValidationConverter implements ValueConverter {

    private static final String VALID = "valid";
    private static final String INVALID = "invalid";

    @Override
    public boolean supports(Type type) {
        return Validation.class.isAssignableFrom(GenericTypes.rawClass(type));
    }

    @Override
    public @Nullable Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
        if (value.isNull()) {
            return null;
        }
        if (value.size() != 1 || !(value.has(VALID) || value.has(INVALID))) {
            throw new ConversionException("a Validation is written as { valid: … } or { invalid: … }, got " + value);
        }
        Type errorType = GenericTypes.typeArgument(targetType, Validation.class, 0);
        Type validType = GenericTypes.typeArgument(targetType, Validation.class, 1);
        return value.has(VALID)
                ? Validation.valid(ctx.fromYaml(value.get(VALID), validType))
                : Validation.invalid(ctx.fromYaml(value.get(INVALID), errorType));
    }

    @Override
    public JsonNode toJson(Object value, ConversionContext ctx) {
        Validation<?, ?> validation = (Validation<?, ?>) value;
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        return validation.isValid()
                ? json.set(VALID, ctx.toJson(validation.get()))
                : json.set(INVALID, ctx.toJson(validation.getError()));
    }

    @Override
    public Optional<Type> wrappedType(Type targetType) {
        return Optional.of(GenericTypes.typeArgument(targetType, Validation.class, 1));
    }

    @Override
    public Object wrap(Object value, Type targetType, ConversionContext ctx) {
        return Validation.valid(value);
    }
}
