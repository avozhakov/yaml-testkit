package com.yamltestkit.infrastructure.mockito;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.ThrowSpec;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.RequiredArgsConstructor;
import org.mockito.stubbing.Answer;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the Mockito answer of a stub. Values are converted and exceptions checked once, while stubbing, so a broken
 * stub fails the case at once instead of inside a call the code under test might catch.
 */
@RequiredArgsConstructor
final class AnswerFactory {

    private final ValueConverters converters;
    private final ExceptionFactory exceptions;

    /**
     * @param declaredType the type the mock stands for, to resolve generic parameter and return types
     * @throws CaseDefinitionException when the answer can't work for the method: a {@code returnsArg} that is out
     *                                 of range or whose argument neither is nor can be wrapped into the return type,
     *                                 an undeclared checked exception, a non-null value for a {@code void} method
     * @throws com.yamltestkit.convert.ConversionException when a value doesn't convert to the return type
     */
    Answer<Object> answer(StubAnswer answer, Method method, Type declaredType, String where) {
        Type returnType = GenericTypes.resolve(method.getGenericReturnType(), declaredType);
        return switch (answer) {
            case StubAnswer.Returns returns -> returning(returns, method, returnType, where);
            case StubAnswer.ReturnsArg returnsArg -> returningArgument(returnsArg.index(), method, declaredType,
                    returnType, where);
            case StubAnswer.Throws throwing -> throwing(throwing.exception(), method, where);
            case StubAnswer.Consecutive consecutive -> consecutive(consecutive.answers(), method, declaredType,
                    where);
        };
    }

    private Answer<Object> returning(StubAnswer.Returns returns, Method method, Type returnType, String where) {
        if (method.getReturnType() == void.class) {
            if (!returns.value().isNull()) {
                throw new CaseDefinitionException(where + ": " + TypeNames.signature(method)
                        + " returns void; use `returns: null` or leave the method unstubbed");
            }
            return invocation -> null;
        }
        Object value = converters.fromYaml(returns.value(), returnType, where + ", returns");
        return invocation -> value;
    }

    /**
     * The argument itself when its parameter type fits the return type, otherwise wrapped by the return type's
     * converter, which must accept the parameter type; both checked here, not when the code under test calls.
     */
    private Answer<Object> returningArgument(int index, Method method, Type declaredType, Type returnType,
                                             String where) {
        if (index < 0 || index >= method.getParameterCount()) {
            throw new CaseDefinitionException(where + ": returnsArg " + index + " is out of range for "
                    + TypeNames.signature(method));
        }
        Type parameterType = GenericTypes.resolve(method.getGenericParameterTypes()[index], declaredType);
        if (accepts(returnType, parameterType)) {
            return invocation -> invocation.getArgument(index);
        }
        converters.wrappedType(returnType)
                .filter(wrapped -> accepts(wrapped, parameterType))
                .orElseThrow(() -> new CaseDefinitionException(where + ": returnsArg " + index + " of "
                        + TypeNames.signature(method) + " is a " + parameterType.getTypeName()
                        + ", which is not a " + returnType.getTypeName() + " and can't be wrapped into one"));
        return invocation -> {
            Object argument = invocation.getArgument(index);
            return argument == null ? null : converters.wrap(argument, returnType, where + ", returnsArg");
        };
    }

    /** Whether every value of {@code valueType} is a {@code targetType}, primitives and their boxes alike. */
    private static boolean accepts(Type targetType, Type valueType) {
        return ClassUtils.resolvePrimitiveIfNecessary(GenericTypes.rawClass(targetType))
                .isAssignableFrom(ClassUtils.resolvePrimitiveIfNecessary(GenericTypes.rawClass(valueType)));
    }

    private Answer<Object> throwing(ThrowSpec spec, Method method, String where) {
        Throwable sample = exceptions.create(spec, where + ", throws");
        boolean checked = sample instanceof Exception && !(sample instanceof RuntimeException);
        if (checked && Arrays.stream(method.getExceptionTypes()).noneMatch(type -> type.isInstance(sample))) {
            throw new CaseDefinitionException(where + ": " + TypeNames.signature(method) + " does not declare the"
                    + " checked exception " + sample.getClass().getName());
        }
        return invocation -> {
            throw exceptions.create(spec, where + ", throws");
        };
    }

    private Answer<Object> consecutive(List<StubAnswer> answers, Method method, Type declaredType, String where) {
        List<Answer<Object>> built = answers.stream()
                .map(answer -> answer(answer, method, declaredType, where))
                .toList();
        AtomicInteger calls = new AtomicInteger();
        return invocation -> built.get(Math.min(calls.getAndIncrement(), built.size() - 1)).answer(invocation);
    }
}
