package com.yamltestkit.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.yamltestkit.infrastructure.reflect.Constructors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * The converter chain: user converters → protobuf (when {@code protobuf-java-util} is on the classpath) → vavr
 * {@code Validation} (when vavr is) → {@code CompletableFuture} → {@code Optional} → builder-style classes →
 * Jackson 2 fallback. The first converter that supports a type converts it. The optional converters are loaded by
 * class name, so the chain never links against protobuf or vavr itself.
 */
public final class ValueConverters {

    private static final List<OptionalLibrary> OPTIONAL_CONVERTERS = List.of(
            new OptionalLibrary("com.google.protobuf.util.JsonFormat",
                    "com.yamltestkit.convert.protobuf.ProtobufConverter"),
            new OptionalLibrary("io.vavr.control.Validation", "com.yamltestkit.convert.vavr.ValidationConverter"));

    private final List<ValueConverter> chain;
    private final ConversionContext jsonContext = new Context("JSON conversion");

    ValueConverters(List<ValueConverter> leading) {
        JacksonConverter jackson = new JacksonConverter(new ConverterModule(this));
        List<ValueConverter> converters = new ArrayList<>(leading);
        converters.add(new BuilderConverter(jackson));
        converters.add(jackson);
        this.chain = List.copyOf(converters);
    }

    /**
     * @param userConverters converters tried before the built-in ones, in order; each needs a public no-argument
     *                       constructor
     */
    public static ValueConverters create(List<Class<? extends ValueConverter>> userConverters) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<ValueConverter> leading = new ArrayList<>();
        userConverters.forEach(type -> leading.add(instantiate(type)));
        for (OptionalLibrary optional : OPTIONAL_CONVERTERS) {
            if (ClassUtils.isPresent(optional.libraryClass(), classLoader)) {
                leading.add(instantiate(load(optional.converterClass(), classLoader)));
            }
        }
        leading.add(new CompletableFutureConverter());
        leading.add(new OptionalConverter());
        return new ValueConverters(leading);
    }

    /**
     * @param location the YAML location of the value, named in errors
     * @throws ConversionException naming the type, the location and the converters tried
     */
    public @Nullable Object fromYaml(JsonNode value, Type targetType, String location) {
        return new Context(location).fromYaml(value, targetType);
    }

    /**
     * @throws ConversionException naming the value's type and the converters tried
     */
    public JsonNode toJson(@Nullable Object value) {
        return jsonContext.toJson(value);
    }

    /**
     * Wraps a value into a wrapper type such as {@code Optional<T>}, {@code CompletableFuture<T>} or
     * {@code Validation<E, T>}, for {@code returnsArg}.
     *
     * @throws ConversionException when no converter can wrap into the type
     */
    public Object wrap(Object value, Type targetType, String location) {
        int selected = select(targetType, false);
        try {
            return chain.get(selected).wrap(value, targetType, new Context(location));
        } catch (UnsupportedOperationException e) {
            throw new ConversionException("no converter can wrap a " + value.getClass().getName() + " into "
                    + targetType.getTypeName() + " at " + location + "; converters tried: " + tried(selected), e);
        }
    }

    /**
     * The type of value {@link #wrap} accepts for {@code targetType}, by the converter chosen for that type; empty
     * when that converter doesn't wrap.
     */
    public Optional<Type> wrappedType(Type targetType) {
        return chain.get(select(targetType, false)).wrappedType(targetType);
    }

    ConversionContext jsonContext() {
        return jsonContext;
    }

    /**
     * The converter of a value nested in a Jackson-converted object, or {@code null} when Jackson itself handles it.
     * Builder-style objects are written by Jackson, so {@code forJson} skips the builder converter.
     */
    @Nullable ValueConverter nestedConverter(Type type, boolean forJson) {
        ValueConverter converter = chain.get(select(type, forJson));
        return converter instanceof JacksonConverter ? null : converter;
    }

    private int select(Type type, boolean forJson) {
        for (int i = 0; i < chain.size(); i++) {
            ValueConverter converter = chain.get(i);
            if (!(forJson && converter instanceof BuilderConverter) && converter.supports(type)) {
                return i;
            }
        }
        throw new IllegalStateException("the Jackson fallback supports every type");
    }

    private String tried(int selected) {
        return chain.subList(0, selected + 1).stream()
                .map(converter -> converter.getClass().getSimpleName())
                .collect(Collectors.joining(" → "));
    }

    private static Class<?> load(String className, ClassLoader classLoader) {
        try {
            return ClassUtils.forName(className, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("built-in converter " + className + " is missing", e);
        }
    }

    private static ValueConverter instantiate(Class<?> type) {
        if (!ValueConverter.class.isAssignableFrom(type)) {
            throw new IllegalStateException(type.getName() + " does not implement " + ValueConverter.class.getName());
        }
        Constructor<?> constructor;
        try {
            constructor = type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("converter " + type.getName() + " needs a no-argument constructor", e);
        }
        constructor.trySetAccessible();
        return (ValueConverter) Constructors.instantiate(constructor, new Object[0], "converter " + type.getName());
    }

    /** A built-in converter that is registered only when its library is on the classpath. */
    private record OptionalLibrary(String libraryClass, String converterClass) {
    }

    /** A failure already described by the chain, passed through nested conversions unchanged. */
    private static final class ChainConversionException extends ConversionException {

        private ChainConversionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final class Context implements ConversionContext {

        private final String location;

        private Context(String location) {
            this.location = location;
        }

        @Override
        public @Nullable Object fromYaml(JsonNode value, Type targetType) {
            int selected = select(targetType, false);
            try {
                return chain.get(selected).fromYaml(value, targetType, this);
            } catch (ChainConversionException e) {
                throw e;
            } catch (RuntimeException e) {
                throw failure("YAML " + abbreviate(value) + " to " + targetType.getTypeName(), selected, e);
            }
        }

        @Override
        public JsonNode toJson(@Nullable Object value) {
            if (value == null) {
                return NullNode.getInstance();
            }
            int selected = select(value.getClass(), true);
            try {
                return chain.get(selected).toJson(value, this);
            } catch (ChainConversionException e) {
                throw e;
            } catch (RuntimeException e) {
                throw failure("a " + value.getClass().getName() + " to JSON", selected, e);
            }
        }

        private ConversionException failure(String what, int selected, RuntimeException cause) {
            return new ChainConversionException("can't convert " + what + " at " + location + "; "
                    + chain.get(selected).getClass().getSimpleName() + " failed: " + cause.getMessage()
                    + "; converters tried: " + tried(selected), cause);
        }
    }
}
