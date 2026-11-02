package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

/**
 * The configuration files at the root folder of a {@code @YamlCases} glob: {@code _context.yaml} and
 * {@code _caseDefaults.yaml}. A file that is missing gives {@code null} / {@link StubSet#EMPTY}; a file that can't be
 * loaded is kept as {@code failure}, reported by every {@code http} / {@code bean} case below the root.
 *
 * @param root           the glob root, e.g. {@code cases/}
 * @param context        the loaded {@code _context.yaml}, {@code null} when missing or broken
 * @param defaults       the stubs of {@code _caseDefaults.yaml}, applied before each {@code http} / {@code bean} case
 * @param defaultsLabel  how {@code _caseDefaults.yaml} is named in messages
 * @param failure        why a configuration file could not be loaded, {@code null} when both loaded
 */
public record RootConfiguration(String root, @Nullable ContextFile context, StubSet defaults, String defaultsLabel,
                                @Nullable String failure) {

    public static final String CONTEXT_FILE = "_context.yaml";
    public static final String DEFAULTS_FILE = "_caseDefaults.yaml";
}
