package com.yamltestkit.infrastructure.reflect;

import org.springframework.core.ResolvableType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Resolves the type variables of a method's generic parameter or return type against the declared type of the
 * object it is called on, e.g. {@code T get()} of a dependency declared as {@code Supplier<Map<String, byte[]>>}
 * becomes {@code Map<String, byte[]>}. Unresolvable variables become their bound.
 */
public final class GenericTypes {

    private GenericTypes() {
    }

    public static Type resolve(Type type, Type owner) {
        return toType(ResolvableType.forType(type, ResolvableType.forType(owner)));
    }

    /** The raw class of a type, {@code Object} for an unresolvable one. */
    public static Class<?> rawClass(Type type) {
        return ResolvableType.forType(type).toClass();
    }

    /**
     * The type argument at {@code index} of {@code genericType} as implemented by {@code type}, e.g. {@code String}
     * for {@code (Optional<String>, Optional.class, 0)}; {@code Object} when {@code type} is raw.
     */
    public static Type typeArgument(Type type, Class<?> genericType, int index) {
        return toType(ResolvableType.forType(type).as(genericType).getGeneric(index));
    }

    /** A parameterized type {@code raw<arguments>}. */
    public static ParameterizedType parameterized(Class<?> raw, Type... arguments) {
        return new ResolvedParameterizedType(raw, arguments.clone());
    }

    private static Type toType(ResolvableType resolvable) {
        Class<?> raw = resolvable.toClass();
        if (!resolvable.hasGenerics() || resolvable.isArray()) {
            return raw;
        }
        Type[] arguments = Arrays.stream(resolvable.getGenerics()).map(GenericTypes::toType).toArray(Type[]::new);
        return new ResolvedParameterizedType(raw, arguments);
    }

    private record ResolvedParameterizedType(Class<?> raw, Type[] arguments) implements ParameterizedType {

        @Override
        public Type[] getActualTypeArguments() {
            return arguments.clone();
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public Type getOwnerType() {
            return raw.getDeclaringClass();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ParameterizedType that && raw.equals(that.getRawType())
                    && Objects.equals(getOwnerType(), that.getOwnerType())
                    && Arrays.equals(arguments, that.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(arguments) ^ raw.hashCode() ^ Objects.hashCode(getOwnerType());
        }

        @Override
        public String toString() {
            return raw.getName() + Arrays.stream(arguments).map(Type::getTypeName)
                    .collect(Collectors.joining(", ", "<", ">"));
        }
    }
}
