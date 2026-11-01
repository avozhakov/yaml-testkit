package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A loaded {@code _context.yaml}: how the engine starts the application for the {@code http} / {@code bean} cases
 * below it.
 *
 * @param label       how the file is named in messages, e.g. {@code cases/_context.yaml}
 * @param application fully qualified name of the Spring Boot application class, {@code null} when not given
 * @param base        properties and bean entries of the default variant
 * @param variants    named variants by name, each applied on top of {@code base}
 * @param services    WireMock services, in file order
 * @param converters  class names of additional value converters, in file order
 * @param settleMs    how long to wait after a case before the next case resets mocks and WireMock
 */
public record ContextFile(
        String label,
        @Nullable String application,
        List<String> profiles,
        ContextVariant base,
        Map<String, ContextVariant> variants,
        List<String> services,
        List<String> converters,
        int settleMs
) {

    public ContextFile {
        profiles = List.copyOf(profiles);
        variants = Map.copyOf(variants);
        services = List.copyOf(services);
        converters = List.copyOf(converters);
    }
}
