package com.yamltestkit.convert;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;

/**
 * The last converter of the chain, for every type: Jackson 2 with {@code java.time} support, records through their
 * canonical constructors, field visibility (so plain value classes convert both ways); unknown properties, a
 * {@code null} for a primitive and a fractional number for an integer are rejected rather than dropped, defaulted or
 * truncated. Values nested in a Jackson-converted object go back through the chain via {@link ConverterModule}.
 */
final class JacksonConverter implements ValueConverter {

    /** Attribute under which the reader carries the {@link ConversionContext} to nested converters. */
    static final String CONTEXT_ATTRIBUTE = ConversionContext.class.getName();

    private final ObjectMapper mapper;

    JacksonConverter(Module chainModule) {
        this.mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(chainModule)
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(DeserializationFeature.WRAP_EXCEPTIONS)
                .disable(SerializationFeature.WRAP_EXCEPTIONS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public boolean supports(Type type) {
        return true;
    }

    @Override
    public @Nullable Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
        if (value.isNull()) {
            if (targetType instanceof Class<?> raw && raw.isPrimitive()) {
                throw new ConversionException("YAML null can't be a primitive " + raw.getName());
            }
            return null;
        }
        try {
            return mapper.readerFor(mapper.constructType(targetType))
                    .withAttribute(CONTEXT_ATTRIBUTE, ctx)
                    .readValue(value);
        } catch (JsonProcessingException e) {
            throw new ConversionException(e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public JsonNode toJson(Object value, ConversionContext ctx) {
        try {
            return mapper.readTree(mapper.writeValueAsString(value));
        } catch (IOException e) {
            throw new UncheckedIOException("can't write " + value.getClass().getName() + " as JSON", e);
        }
    }
}
