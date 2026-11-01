package com.yamltestkit.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.yamltestkit.infrastructure.reflect.GenericTypes;

import java.lang.reflect.Type;
import java.util.Optional;

/**
 * {@code Optional<T>}: a YAML value is the content, YAML {@code null} is {@code Optional.empty()} (a Java
 * {@code null} {@code Optional} is never what a case means); in JSON an empty {@code Optional} is {@code null}.
 */
final class OptionalConverter implements ValueConverter {

    @Override
    public boolean supports(Type type) {
        return GenericTypes.rawClass(type) == Optional.class;
    }

    @Override
    public Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
        return value.isNull()
                ? Optional.empty()
                : Optional.ofNullable(ctx.fromYaml(value, GenericTypes.typeArgument(targetType, Optional.class, 0)));
    }

    @Override
    public JsonNode toJson(Object value, ConversionContext ctx) {
        return ((Optional<?>) value).map(ctx::toJson).orElse(NullNode.getInstance());
    }

    @Override
    public Optional<Type> wrappedType(Type targetType) {
        return Optional.of(GenericTypes.typeArgument(targetType, Optional.class, 0));
    }

    @Override
    public Object wrap(Object value, Type targetType, ConversionContext ctx) {
        return Optional.of(value);
    }
}
