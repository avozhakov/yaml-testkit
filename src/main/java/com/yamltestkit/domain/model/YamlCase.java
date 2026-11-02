package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A validated case. A case written with a single {@code call} has one implicit step named {@link Step#IMPLICIT_NAME}.
 *
 * @param fileDefaults stubs from the file's {@code caseDefaults} block, a layer between {@code _caseDefaults.yaml} and
 *                     the case
 * @param stubs        the case's own {@code mocks}, {@code spies} and {@code http} stubs
 * @param context      the variant named by {@code context}, or {@code null} for the default variant
 */
public record YamlCase(
        String name,
        CaseSource source,
        List<Step> steps,
        StubSet fileDefaults,
        StubSet stubs,
        @Nullable String context,
        boolean freshContext
) implements CaseEntry {

    public YamlCase {
        steps = List.copyOf(steps);
    }

    /** How the case is named in failure messages: case file and case, e.g. {@code cases/a.yaml, case 'c'}. */
    public String where() {
        return source.label() + ", case '" + name + "'";
    }

    public Set<CallKind> kinds() {
        Set<CallKind> kinds = EnumSet.noneOf(CallKind.class);
        steps.forEach(step -> kinds.add(step.call().kind()));
        return kinds;
    }
}
