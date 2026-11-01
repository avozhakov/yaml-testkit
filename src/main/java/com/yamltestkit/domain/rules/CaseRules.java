package com.yamltestkit.domain.rules;

import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.VerifyCheck;
import com.yamltestkit.domain.model.YamlCase;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects sections that do not apply to a case's call kinds: Spring-only sections in a case whose calls are all
 * {@code class}, expectations of the wrong call kind, and verification targets that don't exist for the call. Also
 * requires unique step and capture names, since references resolve by name, and rejects {@code returns} together
 * with {@code throws}, which merging the file's {@code defaults} into a case can produce.
 */
public final class CaseRules {

    private CaseRules() {
    }

    public static void check(YamlCase yamlCase) {
        checkUniqueNames(yamlCase);
        if (yamlCase.kinds().equals(Set.of(CallKind.CLASS))) {
            checkUnitOnly(yamlCase);
        }
        yamlCase.steps().forEach(CaseRules::checkStep);
    }

    private static void checkUniqueNames(YamlCase yamlCase) {
        Set<String> names = new HashSet<>();
        for (Step step : yamlCase.steps()) {
            if (!names.add(step.name())) {
                throw new CaseDefinitionException("duplicate step name '" + step.name() + "'");
            }
        }
        for (Step step : yamlCase.steps()) {
            for (VerifyCheck check : step.expect().verify()) {
                if (check.as() != null && !names.add(check.as())) {
                    throw new CaseDefinitionException("step '" + step.name() + "': capture name '" + check.as()
                            + "' is already used by a step or another capture");
                }
            }
        }
    }

    private static void checkUnitOnly(YamlCase yamlCase) {
        String reason = " is not allowed in a case whose calls are all `class` (no Spring context, no WireMock)";
        if (!yamlCase.fileDefaults().isEmpty() || !yamlCase.stubs().isEmpty()) {
            throw new CaseDefinitionException("`mocks` / `spies` / `http`" + reason + "; use `deps` instead");
        }
        if (yamlCase.context() != null) {
            throw new CaseDefinitionException("`context`" + reason);
        }
        if (yamlCase.freshContext()) {
            throw new CaseDefinitionException("`freshContext`" + reason);
        }
        for (Step step : yamlCase.steps()) {
            if (!step.stubs().isEmpty()) {
                throw new CaseDefinitionException("step '" + step.name() + "': `mocks` / `spies` / `http`" + reason
                        + "; use `deps` instead");
            }
        }
    }

    private static void checkStep(Step step) {
        String where = "step '" + step.name() + "' (" + step.call().kind().name().toLowerCase(Locale.ROOT) + " call): ";
        Expect expect = step.expect();
        if (step.call().kind() == CallKind.HTTP && expect.hasReturnParts()) {
            throw new CaseDefinitionException(where + "`returns` / `throws` apply only to bean and class calls");
        }
        if (step.call().kind() != CallKind.HTTP && expect.hasHttpOnlyParts()) {
            throw new CaseDefinitionException(where + "`status` / `headers` / `body` / `bodyText` apply only to http"
                    + " calls");
        }
        if (!expect.returns().isMissingNode() && expect.throwsException() != null) {
            throw new CaseDefinitionException(where + "`returns` and `throws` exclude each other; one of them may come"
                    + " from the file's `defaults`");
        }
        if (step.call().kind() == CallKind.CLASS && expect.context() != null) {
            throw new CaseDefinitionException(where + "`expect.context` needs an application context");
        }
        expect.verify().forEach(check -> checkVerifyTarget(check, step.call().kind(), where));
    }

    private static void checkVerifyTarget(VerifyCheck check, CallKind kind, String where) {
        boolean unit = kind == CallKind.CLASS;
        switch (check) {
            case VerifyCheck.OnService service when unit -> throw new CaseDefinitionException(where
                    + "verify on service '" + service.service() + "' needs WireMock, which class calls don't use");
            case VerifyCheck.OnMock mock when unit && mock.target() == VerifyCheck.MockTarget.BEAN ->
                    throw new CaseDefinitionException(where + "verify on bean '" + mock.name()
                            + "': class calls have no beans; use `dep`");
            case VerifyCheck.OnMock mock when !unit && mock.target() == VerifyCheck.MockTarget.DEP ->
                    throw new CaseDefinitionException(where + "verify on dep '" + mock.name()
                            + "': `dep` applies only to class calls; use `bean`");
            default -> {
            }
        }
    }
}
