package com.yamltestkit.infrastructure.mockito;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import com.yamltestkit.infrastructure.reflect.MethodResolver;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Applies stubs of the shared stub grammar to a Mockito mock or spy: mocks and spies of beans, boot-time stubs and
 * unit {@code deps}. Stubbing uses the {@code doAnswer(…).when(mock)} form, so a spy's real method is not invoked
 * while stubbing. Stubs of the same method with different {@code args} coexist; for overlapping {@code args} the
 * stub applied last wins. Static and private methods are rejected: Mockito's inline mock maker intercepts neither,
 * so such a stub would silently never answer (final methods are intercepted and can be stubbed).
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class StubApplier {

    private final AnswerFactory answers;
    private final ArgMatcherFactory matchers;

    public static StubApplier create(ValueConverters converters) {
        return new StubApplier(new AnswerFactory(converters, new ExceptionFactory(converters)),
                new ArgMatcherFactory(converters));
    }

    /**
     * @param declaredType the type the mock or spy stands for (e.g. the declared constructor parameter type), used
     *                     to find methods and to resolve generic parameter and return types
     * @param errors       collects errors the stubs raise while the code under test runs, for the caller to report
     * @param where        how the YAML location is named in errors
     */
    public void apply(Object mock, Type declaredType, List<Stub> stubs, StubErrors errors, String where) {
        stubs.forEach(stub -> apply(mock, declaredType, stub, errors, where + ", stub " + stub.method().name()));
    }

    private void apply(Object mock, Type declaredType, Stub stub, StubErrors errors, String where) {
        Method method = MethodResolver.resolve(GenericTypes.rawClass(declaredType), stub.method(), where);
        rejectUnstubbable(method, where);
        Answer<Object> answer = answers.answer(stub.answer(), method, declaredType, where);
        List<ArgumentMatcher<Object>> argumentMatchers = matchers.forMethod(stub.args(), method, declaredType, errors,
                where);
        Object stubbing = Mockito.doAnswer(answer).when(mock);
        argumentMatchers.forEach(ArgumentMatchers::argThat);
        invoke(method, stubbing, placeholders(method));
        Mockito.validateMockitoUsage();
    }

    private static void rejectUnstubbable(Method method, String where) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new CaseDefinitionException(where + ": " + TypeNames.signature(method)
                    + " is static; static methods can't be stubbed");
        }
        if (Modifier.isPrivate(method.getModifiers())) {
            throw new CaseDefinitionException(where + ": " + TypeNames.signature(method)
                    + " is private; private methods can't be stubbed");
        }
    }

    /** Arguments passed while Mockito records the matchers: ignored, but primitives can't be {@code null}. */
    private static Object[] placeholders(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        Object[] values = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            values[i] = parameters[i].isPrimitive() ? Array.get(Array.newInstance(parameters[i], 1), 0) : null;
        }
        return values;
    }

    private static void invoke(Method method, Object target, Object[] args) {
        try {
            method.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("stubbing " + method + " failed", e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("can't stub " + method, e);
        }
    }
}
