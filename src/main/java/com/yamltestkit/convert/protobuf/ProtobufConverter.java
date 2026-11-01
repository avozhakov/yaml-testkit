package com.yamltestkit.convert.protobuf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.yamltestkit.convert.ConversionContext;
import com.yamltestkit.convert.ConversionException;
import com.yamltestkit.convert.ValueConverter;
import com.yamltestkit.domain.json.JsonMappers;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

/**
 * Protobuf messages through {@link JsonFormat}: YAML in proto3 JSON form (lowerCamel or original field names,
 * unknown fields rejected); JSON output includes default-valued fields, so a {@code 0} or empty field can be
 * asserted. Note that proto3 JSON writes 64-bit integers as strings. Registered by class name only when
 * {@code protobuf-java-util} is on the classpath.
 */
public final class ProtobufConverter implements ValueConverter {

    private final JsonFormat.Parser parser = JsonFormat.parser();
    private final JsonFormat.Printer printer = JsonFormat.printer().includingDefaultValueFields();

    @Override
    public boolean supports(Type type) {
        Class<?> raw = GenericTypes.rawClass(type);
        return Message.class.isAssignableFrom(raw) && !raw.isInterface() && !Modifier.isAbstract(raw.getModifiers());
    }

    @Override
    public @Nullable Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
        if (value.isNull()) {
            return null;
        }
        Class<?> raw = GenericTypes.rawClass(targetType);
        try {
            Message.Builder builder = (Message.Builder) raw.getMethod("newBuilder").invoke(null);
            parser.merge(value.toString(), builder);
            return builder.build();
        } catch (InvalidProtocolBufferException e) {
            throw new ConversionException(e.getMessage(), e);
        } catch (ReflectiveOperationException e) {
            throw new ConversionException(raw.getName() + " has no static newBuilder()", e);
        }
    }

    @Override
    public JsonNode toJson(Object value, ConversionContext ctx) {
        try {
            return JsonMappers.plain().readTree(printer.print((Message) value));
        } catch (InvalidProtocolBufferException | JsonProcessingException e) {
            throw new ConversionException("can't print " + value.getClass().getName() + ": " + e.getMessage(), e);
        }
    }
}
