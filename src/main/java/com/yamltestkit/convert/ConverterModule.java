package com.yamltestkit.convert;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeBindings;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Lets the Jackson fallback hand nested values to the rest of the chain: a protobuf message inside a record, an
 * {@code Optional} field, a {@code List} of builder-style objects.
 */
@RequiredArgsConstructor
final class ConverterModule extends Module {

    private final ValueConverters chain;

    @Override
    public String getModuleName() {
        return "yaml-testkit-converters";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addDeserializers(new Deserializers.Base() {
            @Override
            public @Nullable JsonDeserializer<?> findBeanDeserializer(JavaType type, DeserializationConfig config,
                                                                      BeanDescription beanDesc) {
                Type reflectType = reflectType(type);
                ValueConverter converter = chain.nestedConverter(reflectType, false);
                return converter == null ? null : new ChainDeserializer(converter, reflectType);
            }
        });
        context.addSerializers(new Serializers.Base() {
            @Override
            public @Nullable JsonSerializer<?> findSerializer(SerializationConfig config, JavaType type,
                                                              BeanDescription beanDesc) {
                ValueConverter converter = chain.nestedConverter(reflectType(type), true);
                return converter == null ? null : new ChainSerializer(converter, chain);
            }
        });
    }

    private static Type reflectType(JavaType type) {
        TypeBindings bindings = type.getBindings();
        if (bindings.isEmpty() || type.isArrayType()) {
            return type.getRawClass();
        }
        Type[] arguments = new Type[bindings.size()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = reflectType(bindings.getBoundType(i));
        }
        return GenericTypes.parameterized(type.getRawClass(), arguments);
    }

    private static final class ChainDeserializer extends StdDeserializer<Object> {

        private final transient ValueConverter converter;
        private final transient Type type;

        private ChainDeserializer(ValueConverter converter, Type type) {
            super(Object.class);
            this.converter = converter;
            this.type = type;
        }

        @Override
        public @Nullable Object deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            JsonNode node = ctxt.readTree(parser);
            return converter.fromYaml(node, type, context(ctxt));
        }

        @Override
        public @Nullable Object getNullValue(DeserializationContext ctxt) {
            return converter.fromYaml(NullNode.getInstance(), type, context(ctxt));
        }

        private static ConversionContext context(DeserializationContext ctxt) {
            return (ConversionContext) ctxt.getAttribute(JacksonConverter.CONTEXT_ATTRIBUTE);
        }
    }

    private static final class ChainSerializer extends StdSerializer<Object> {

        private final transient ValueConverter converter;
        private final transient ValueConverters chain;

        private ChainSerializer(ValueConverter converter, ValueConverters chain) {
            super(Object.class);
            this.converter = converter;
            this.chain = chain;
        }

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeTree(converter.toJson(value, chain.jsonContext()));
        }
    }
}
