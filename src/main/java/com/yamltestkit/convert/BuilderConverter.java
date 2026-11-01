package com.yamltestkit.convert;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builder-style classes that Jackson can't create (no record, no no-argument constructor, no {@code @JsonCreator},
 * no {@code @JsonDeserialize}): a static {@code builder()} whose builder takes each YAML field through
 * {@code field(v)} or {@code setField(v)}, then {@code build()}. Covers Lombok {@code @Builder} and hand-written
 * {@code builder().setX()} classes. To JSON such objects go through the Jackson fallback.
 */
@RequiredArgsConstructor
final class BuilderConverter implements ValueConverter {

    private final JacksonConverter jackson;

    @Override
    public boolean supports(Type type) {
        Class<?> raw = GenericTypes.rawClass(type);
        return !raw.isInterface() && !raw.isPrimitive() && !raw.isArray() && !jacksonHasCreator(raw)
                && builderMethod(raw) != null;
    }

    @Override
    public @Nullable Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
        if (value.isNull()) {
            return null;
        }
        if (!value.isObject()) {
            throw new ConversionException("a builder-style object needs a YAML object, got " + value.getNodeType());
        }
        Method builderMethod = builderMethod(GenericTypes.rawClass(targetType));
        try {
            Object builder = builderMethod.invoke(null);
            for (Map.Entry<String, JsonNode> field : value.properties()) {
                Method setter = setter(builder.getClass(), field.getKey());
                setter.invoke(builder, ctx.fromYaml(field.getValue(), setter.getGenericParameterTypes()[0]));
            }
            Method build = builder.getClass().getMethod("build");
            build.trySetAccessible();
            return build.invoke(builder);
        } catch (InvocationTargetException e) {
            throw new ConversionException("the builder threw " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new ConversionException("can't use the builder of " + targetType.getTypeName() + ": " + e, e);
        }
    }

    @Override
    public JsonNode toJson(Object value, ConversionContext ctx) {
        return jackson.toJson(value, ctx);
    }

    private static Method setter(Class<?> builderType, String field) {
        String setterName = "set" + field.substring(0, 1).toUpperCase(Locale.ROOT) + field.substring(1);
        List<Method> candidates = Arrays.stream(builderType.getMethods())
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getName().equals(field) || method.getName().equals(setterName))
                .toList();
        if (candidates.size() != 1) {
            throw new ConversionException("builder " + builderType.getName() + " has "
                    + (candidates.isEmpty() ? "no" : "more than one") + " method " + field + "(…) / " + setterName
                    + "(…) for the YAML key '" + field + "'");
        }
        Method setter = candidates.getFirst();
        setter.trySetAccessible();
        return setter;
    }

    private static @Nullable Method builderMethod(Class<?> raw) {
        try {
            Method builder = raw.getDeclaredMethod("builder");
            if (!Modifier.isStatic(builder.getModifiers())) {
                return null;
            }
            Method build = builder.getReturnType().getMethod("build");
            if (!raw.isAssignableFrom(build.getReturnType())) {
                return null;
            }
            builder.trySetAccessible();
            return builder;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean jacksonHasCreator(Class<?> raw) {
        if (raw.isRecord() || raw.isAnnotationPresent(JsonDeserialize.class)) {
            return true;
        }
        for (Constructor<?> constructor : raw.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0 || constructor.isAnnotationPresent(JsonCreator.class)) {
                return true;
            }
        }
        return Arrays.stream(raw.getDeclaredMethods())
                .anyMatch(method -> Modifier.isStatic(method.getModifiers())
                        && method.isAnnotationPresent(JsonCreator.class));
    }
}
