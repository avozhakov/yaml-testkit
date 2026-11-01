package com.yamltestkit.infrastructure.mockito;

import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.infrastructure.reflect.TypeNames;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Matches {@code deps} keys to constructor parameters: by name first, else by a type only one has. */
final class DependencyMatcher {

    private DependencyMatcher() {
    }

    static Map<Integer, String> match(Set<String> keys, Parameter[] parameters, Class<?> type, String where) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        if (parameters.length > 0 && !parameters[0].isNamePresent()) {
            throw new CaseDefinitionException(where + ": constructor parameter names of " + type.getName()
                    + " are not available (found " + parameters[0].getName() + "); compile it with `-parameters`"
                    + " so `deps` can match them");
        }
        Map<Integer, String> matched = new HashMap<>();
        for (String key : keys) {
            int index = indexOf(key, parameters, type, where);
            String previous = matched.put(index, key);
            if (previous != null) {
                throw new CaseDefinitionException(where + ": deps '" + previous + "' and '" + key
                        + "' both match parameter " + describe(parameters[index]));
            }
        }
        return matched;
    }

    private static int indexOf(String key, Parameter[] parameters, Class<?> type, String where) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(key)) {
                return i;
            }
        }
        List<Integer> byType = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            if (TypeNames.matches(key, parameters[i].getType())) {
                byType.add(i);
            }
        }
        if (byType.size() == 1) {
            return byType.getFirst();
        }
        if (byType.isEmpty()) {
            throw new CaseDefinitionException(where + ": deps '" + key + "' matches no constructor parameter of "
                    + type.getName() + " by name or type; parameters: " + describeAll(parameters));
        }
        throw new CaseDefinitionException(where + ": deps '" + key + "' is ambiguous: parameters "
                + byType.stream().map(i -> parameters[i].getName()).collect(Collectors.joining(" and "))
                + " both have type " + key + "; use a parameter name");
    }

    static String describe(Parameter parameter) {
        return parameter.getName() + " (" + parameter.getType().getSimpleName() + ")";
    }

    private static String describeAll(Parameter[] parameters) {
        return Arrays.stream(parameters).map(DependencyMatcher::describe).collect(Collectors.joining(", "));
    }
}
