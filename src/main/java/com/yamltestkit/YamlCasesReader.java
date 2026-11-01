package com.yamltestkit;

import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.engine.SuiteSettings;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the {@link SuiteSettings} of a suite class from its {@link YamlCases} and the
 * {@value SuiteSettings#FILTER_PROPERTY} system property. It lives next to the annotation, so the engine never
 * depends on the public API package.
 */
final class YamlCasesReader {

    private YamlCasesReader() {
    }

    static SuiteSettings read(Class<?> suiteClass) {
        YamlCases annotation = suiteClass.getAnnotation(YamlCases.class);
        if (annotation == null) {
            throw new IllegalStateException(suiteClass.getName() + " extends YamlCaseSuite but has no @YamlCases");
        }
        Set<CallKind> kinds = annotation.kinds().length == 0
                ? EnumSet.noneOf(CallKind.class)
                : EnumSet.copyOf(Arrays.asList(annotation.kinds()));
        return new SuiteSettings(List.of(annotation.value()), kinds, List.of(annotation.converters()),
                System.getProperty(SuiteSettings.FILTER_PROPERTY));
    }
}
