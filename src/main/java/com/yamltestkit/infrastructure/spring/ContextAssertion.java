package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.domain.model.ContextExpectation;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code expect.context}: bean names ({@code beans}) and bean types ({@code beanTypes}, by fully qualified name,
 * subtypes included) that must be present in, or absent from, the case's application context.
 */
public final class ContextAssertion {

    /**
     * @param where the step, prefixed to failure messages
     * @return the failed expectations, empty when all hold
     * @throws com.yamltestkit.domain.model.CaseDefinitionException when a {@code beanTypes} class does not exist
     */
    public List<AssertionFailedError> check(ContextExpectation expected, ApplicationContext context, String where) {
        List<AssertionFailedError> failures = new ArrayList<>();
        expected.beans().present().stream()
                .filter(name -> !context.containsBean(name))
                .forEach(name -> failures.add(new AssertionFailedError(where + ": context: expected bean '" + name
                        + "' but the context has no bean of that name")));
        expected.beans().absent().stream()
                .filter(context::containsBean)
                .forEach(name -> failures.add(new AssertionFailedError(where + ": context: expected no bean '" + name
                        + "' but the context has one of type " + typeName(context.getType(name)))));
        String location = where + ": context.beanTypes";
        expected.beanTypes().present().forEach(type -> {
            if (beansOf(context, type, location).length == 0) {
                failures.add(new AssertionFailedError(where + ": context: expected a bean of type " + type
                        + " but the context has none"));
            }
        });
        expected.beanTypes().absent().forEach(type -> {
            String[] names = beansOf(context, type, location);
            if (names.length > 0) {
                failures.add(new AssertionFailedError(where + ": context: expected no bean of type " + type
                        + " but the context has " + Arrays.toString(names)));
            }
        });
        return failures;
    }

    private static String typeName(@Nullable Class<?> type) {
        return type == null ? "<unknown>" : type.getName();
    }

    private static String[] beansOf(ApplicationContext context, String type, String where) {
        return context.getBeanNamesForType(TypeNames.load(type, where));
    }
}
