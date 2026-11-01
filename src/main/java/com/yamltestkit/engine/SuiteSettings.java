package com.yamltestkit.engine;

import com.yamltestkit.convert.ValueConverter;
import com.yamltestkit.domain.model.CallKind;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * What a suite runs: the case-file globs, the selected call kinds, extra converters and the optional
 * {@code yamlit.filter} glob over case paths ({@code <file path relative to the glob root>/<case name>}).
 */
public record SuiteSettings(List<String> globs, Set<CallKind> kinds, List<Class<? extends ValueConverter>> converters,
                            @Nullable String filter) {

    public static final String FILTER_PROPERTY = "yamlit.filter";

    public SuiteSettings {
        if (globs.isEmpty()) {
            throw new IllegalArgumentException("@YamlCases declares no glob");
        }
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("@YamlCases declares no call kind");
        }
        globs = List.copyOf(globs);
        kinds = Set.copyOf(kinds);
        converters = List.copyOf(converters);
        filter = filter == null || filter.isBlank() ? null : filter.strip();
    }
}
