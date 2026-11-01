package com.yamltestkit.domain.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.Assertion;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.ValueRef;
import com.yamltestkit.domain.model.VerifyCheck;
import com.yamltestkit.domain.model.YamlCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that every {@code { ref }} names a value produced earlier in the same case. Producers in order: each step's
 * result (available to its own and later expectations), and inside a step each {@code verify[].as} capture in list
 * order (available to later checks, to the step's other expectations and to later steps).
 */
public final class ReferenceOrderCheck {

    private ReferenceOrderCheck() {
    }

    public static void check(YamlCase yamlCase) {
        Map<String, String> producers = producers(yamlCase);
        Set<String> available = new LinkedHashSet<>();
        for (Step step : yamlCase.steps()) {
            available.add(step.name());
            Expect expect = step.expect();
            for (VerifyCheck check : expect.verify()) {
                requireAvailable(refsOf(check), available, producers, step);
                if (check.as() != null) {
                    available.add(check.as());
                }
            }
            requireAvailable(refsOf(expect), available, producers, step);
        }
    }

    /** Every producer name mapped to where it is defined, for "defined later" messages. */
    private static Map<String, String> producers(YamlCase yamlCase) {
        Map<String, String> producers = new HashMap<>();
        for (Step step : yamlCase.steps()) {
            producers.put(step.name(), "step '" + step.name() + "'");
            step.expect().verify().stream()
                    .filter(check -> check.as() != null)
                    .forEach(check -> producers.put(check.as(), "verify capture of step '" + step.name() + "'"));
        }
        return producers;
    }

    private static void requireAvailable(List<ValueRef> refs, Set<String> available, Map<String, String> producers,
                                         Step step) {
        for (ValueRef ref : refs) {
            if (available.contains(ref.name())) {
                continue;
            }
            String definedAt = producers.get(ref.name());
            throw new CaseDefinitionException("step '" + step.name() + "': "
                    + (definedAt == null
                    ? "unknown reference '" + ref.name() + "'"
                    : "forward reference '" + ref.name() + "' (defined later, as " + definedAt + ")")
                    + "; references available here: " + available);
        }
    }

    private static List<ValueRef> refsOf(VerifyCheck check) {
        List<ValueRef> refs = new ArrayList<>();
        if (check instanceof VerifyCheck.OnMock mock && mock.args() != null) {
            mock.args().stream()
                    .filter(ArgMatcher.Literal.class::isInstance)
                    .map(matcher -> ((ArgMatcher.Literal) matcher).value())
                    .forEach(value -> addIfRef(value, refs));
        }
        check.asserts().forEach(assertion -> collect(assertion, refs));
        return refs;
    }

    private static List<ValueRef> refsOf(Expect expect) {
        List<ValueRef> refs = new ArrayList<>();
        if (expect.body() != null) {
            addIfRef(expect.body().expected(), refs);
        }
        addIfRef(expect.returns(), refs);
        expect.asserts().forEach(assertion -> collect(assertion, refs));
        return refs;
    }

    private static void collect(Assertion assertion, List<ValueRef> refs) {
        assertion.operators().values().forEach(value -> addIfRef(value, refs));
        if (assertion.all() != null) {
            collect(assertion.all(), refs);
        }
        if (assertion.none() != null) {
            collect(assertion.none(), refs);
        }
    }

    private static void addIfRef(JsonNode value, List<ValueRef> refs) {
        if (ValueRef.isRef(value)) {
            refs.add(ValueRef.of(value));
        }
    }
}
