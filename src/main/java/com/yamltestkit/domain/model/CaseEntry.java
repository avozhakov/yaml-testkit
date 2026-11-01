package com.yamltestkit.domain.model;

/**
 * One entry of a case file: a valid {@link YamlCase} or a {@link BrokenCase} that fails when run.
 */
public sealed interface CaseEntry permits YamlCase, BrokenCase {

    String name();

    CaseSource source();

    /** Path matched by {@code yamlit.filter}: {@code <file path relative to the glob root>/<case name>}. */
    default String casePath() {
        return source().relativePath() + "/" + name();
    }
}
