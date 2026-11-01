package com.yamltestkit.infrastructure.mockito;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Dependency;
import com.yamltestkit.infrastructure.reflect.Constructors;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the object of a {@code call.class} step without Spring: its only constructor (public constructors are
 * preferred over non-public ones) or the one named by {@code constructor}. Each {@code deps} key is matched to a
 * parameter by name, or else by a type (simple or qualified name) that exactly one parameter has; it supplies a mock
 * with {@code stubs}, a converted {@code value}, or a {@code real: true} instance built by the same rules. Every
 * other parameter gets a plain Mockito mock.
 */
@RequiredArgsConstructor
public final class UnitInstanceFactory {

    private final ValueConverters converters;
    private final StubApplier stubs;

    /**
     * @param errors collects errors the dependency stubs raise while the code under test runs
     * @param where  how the YAML location is named in errors
     * @throws CaseDefinitionException for a missing or ambiguous constructor, an unknown or ambiguous dependency
     *                                 key, or parameter names missing because the class was compiled without
     *                                 {@code -parameters}
     * @throws IllegalStateException   naming {@code where} when a constructor throws
     */
    public UnitInstance create(Class<?> type, @Nullable List<String> constructorTypes, Map<String, Dependency> deps,
                               StubErrors errors, String where) {
        return create(type, constructorTypes, deps, errors, where, new HashSet<>());
    }

    /** @param building the classes being built: more than one means this is a {@code real: true} dependency */
    private UnitInstance create(Class<?> type, @Nullable List<String> constructorTypes, Map<String, Dependency> deps,
                                StubErrors errors, String where, Set<Class<?>> building) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new CaseDefinitionException(where + ": " + type.getName() + " is abstract and can't be created");
        }
        if (!building.add(type)) {
            throw new CaseDefinitionException(where + ": `real: true` dependencies form a cycle through "
                    + type.getName());
        }
        Constructor<?> constructor = constructor(type, constructorTypes, where);
        Parameter[] parameters = constructor.getParameters();
        Map<Integer, String> keys = DependencyMatcher.match(deps.keySet(), parameters, type, where);
        boolean real = building.size() > 1;
        Object[] values = new Object[parameters.length];
        Map<String, Object> mocks = new LinkedHashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            String key = keys.get(i);
            values[i] = key == null
                    ? unlistedMock(parameters[i], type, real, where)
                    : resolve(deps.get(key), parameters[i], errors, where + ", deps." + key, building);
            if (key == null || deps.get(key) instanceof Dependency.Stubbed) {
                mocks.put(key == null ? parameters[i].getName() : key, values[i]);
            }
        }
        building.remove(type);
        return new UnitInstance(Constructors.instantiate(constructor, values, where), mocks);
    }

    private @Nullable Object resolve(Dependency dependency, Parameter parameter, StubErrors errors, String where,
                                     Set<Class<?>> building) {
        return switch (dependency) {
            case Dependency.Stubbed stubbed -> {
                Object mock = mock(parameter, where, "give it a `value` or `real: true` instead of `stubs`");
                stubs.apply(mock, parameter.getParameterizedType(), stubbed.stubs(), errors, where);
                yield mock;
            }
            case Dependency.Value value -> converters.fromYaml(value.value(), parameter.getParameterizedType(),
                    where + ".value");
            case Dependency.Real ignored -> create(parameter.getType(), null, Map.of(), errors, where + " (real)",
                    building).instance();
        };
    }

    /**
     * A parameter not listed in {@code deps}. Inside a {@code real: true} dependency nothing can be listed, so the
     * hint points at giving that dependency as a {@code value} instead.
     */
    private static Object unlistedMock(Parameter parameter, Class<?> type, boolean real, String where) {
        return mock(parameter, where, real
                ? "`real: true` builds " + type.getSimpleName() + " with mocks for all its parameters; give this"
                + " dependency as a `value` instead"
                : "list it under `deps` with a `value` or `real: true`");
    }

    private static Object mock(Parameter parameter, String where, String hint) {
        try {
            return Mockito.mock(parameter.getType(), Mockito.withSettings().name(parameter.getName()));
        } catch (MockitoException e) {
            throw new CaseDefinitionException(where + ": parameter " + DependencyMatcher.describe(parameter)
                    + " can't be mocked; " + hint + " (" + e.getMessage().strip().lines().findFirst().orElse("")
                    + ")", e);
        }
    }

    private static Constructor<?> constructor(Class<?> type, @Nullable List<String> constructorTypes, String where) {
        Constructor<?>[] publicConstructors = type.getConstructors();
        List<Constructor<?>> candidates = Arrays.stream(publicConstructors.length > 0 ? publicConstructors
                        : type.getDeclaredConstructors())
                .filter(constructor -> !constructor.isSynthetic())
                .toList();
        List<Constructor<?>> chosen = constructorTypes == null
                ? candidates
                : candidates.stream().filter(candidate -> TypeNames.parametersMatch(constructorTypes, candidate))
                .toList();
        if (chosen.size() == 1) {
            Constructor<?> constructor = chosen.getFirst();
            constructor.trySetAccessible();
            return constructor;
        }
        String list = candidates.stream().map(TypeNames::signature).sorted().collect(Collectors.joining(", "));
        throw new CaseDefinitionException(where + ": " + (constructorTypes == null
                ? type.getName() + " has " + candidates.size() + " constructors; choose one with `constructor`: "
                : "no constructor " + constructorTypes + " on " + type.getName() + "; constructors: ") + list);
    }
}
