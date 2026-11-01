package com.yamltestkit.infrastructure.reflect;

import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.MethodRef;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds the method a YAML {@code method} (+ optional {@code paramTypes}) names: public methods of the type and its
 * supertypes plus the type's own non-public methods, bridge and synthetic methods excluded.
 */
public final class MethodResolver {

    private MethodResolver() {
    }

    /**
     * @param where how the YAML location is named in errors
     * @throws CaseDefinitionException when no method or more than one method matches; the message lists the
     *                                 candidate signatures, sorted, since reflection returns methods in no fixed order
     */
    public static Method resolve(Class<?> type, MethodRef ref, String where) {
        List<Method> named = candidates(type, ref.name());
        if (named.isEmpty()) {
            throw new CaseDefinitionException(where + ": " + type.getName() + " has no method '" + ref.name() + "'");
        }
        List<Method> matching = ref.paramTypes() == null
                ? named
                : named.stream().filter(method -> TypeNames.parametersMatch(ref.paramTypes(), method)).toList();
        if (matching.size() == 1) {
            Method method = matching.getFirst();
            method.trySetAccessible();
            return method;
        }
        String candidates = named.stream().map(TypeNames::signature).sorted().collect(Collectors.joining(", "));
        if (matching.isEmpty()) {
            throw new CaseDefinitionException(where + ": no method " + ref.name() + ref.paramTypes() + " on "
                    + type.getName() + "; candidates: " + candidates);
        }
        throw new CaseDefinitionException(where + ": method '" + ref.name() + "' of " + type.getName()
                + " is ambiguous; add `paramTypes` to choose one of: " + candidates);
    }

    private static List<Method> candidates(Class<?> type, String name) {
        Map<List<Class<?>>, Method> bySignature = new LinkedHashMap<>();
        Stream.concat(Arrays.stream(type.getDeclaredMethods()), Arrays.stream(type.getMethods()))
                .filter(method -> method.getName().equals(name) && !method.isBridge() && !method.isSynthetic())
                .forEach(method -> bySignature.putIfAbsent(List.of(method.getParameterTypes()), method));
        return List.copyOf(bySignature.values());
    }
}
