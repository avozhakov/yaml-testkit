package com.yamltestkit.infrastructure.reflect;

import com.yamltestkit.domain.model.CaseDefinitionException;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Type names as YAML writes them: a type is named by its simple or fully qualified name ({@code int},
 * {@code String}, {@code java.lang.String}, {@code Outer.Inner} or {@code com.example.Outer$Inner}).
 */
public final class TypeNames {

    private TypeNames() {
    }

    /** True when {@code name} is the simple, canonical or binary name of {@code type}. */
    public static boolean matches(String name, Class<?> type) {
        String canonical = type.getCanonicalName();
        return name.equals(type.getSimpleName()) || name.equals(type.getName())
                || (canonical != null && (name.equals(canonical) || canonical.endsWith("." + name)));
    }

    /** True when {@code name} names {@code type} or one of its superclasses. */
    public static boolean matchesHierarchy(String name, Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (matches(name, current)) {
                return true;
            }
        }
        return false;
    }

    /** True when the executable's parameter types are named by {@code names}, in order. */
    public static boolean parametersMatch(List<String> names, Executable executable) {
        Class<?>[] types = executable.getParameterTypes();
        if (types.length != names.size()) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            if (!matches(names.get(i), types[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Loads a class by its fully qualified (binary or canonical) name with the context class loader.
     *
     * @param where how the referencing YAML location is named in the error
     * @throws CaseDefinitionException when no such class exists
     */
    public static Class<?> load(String name, String where) {
        try {
            return ClassUtils.forName(name, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new CaseDefinitionException(where + ": class " + name + " not found (use the fully qualified name)",
                    e);
        }
    }

    /** {@code name(Type1, Type2)} with simple type names, for candidate lists in error messages. */
    public static String signature(Executable executable) {
        String name = executable instanceof Constructor<?> ? executable.getDeclaringClass().getSimpleName()
                : executable.getName();
        String params = Arrays.stream(executable.getGenericParameterTypes())
                .map(type -> type.getTypeName().replaceAll("\\b[a-z][\\w]*\\.", ""))
                .collect(Collectors.joining(", "));
        String prefix = executable instanceof Method method && Modifier.isStatic(method.getModifiers())
                ? "static " : "";
        return prefix + name + "(" + params + ")";
    }
}
