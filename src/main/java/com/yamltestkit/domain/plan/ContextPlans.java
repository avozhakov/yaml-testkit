package com.yamltestkit.domain.plan;

import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.VerifyCheck;
import com.yamltestkit.domain.model.YamlCase;

import java.util.Map;
import java.util.TreeSet;

/**
 * The variant plans of every glob root, and the variants that can't start (a bean both mocked and spied, no
 * {@code application}); looked up per case, which also checks what only the case can get wrong.
 */
public record ContextPlans(Map<String, RootConfiguration> roots, Map<VariantKey, VariantPlan> plans,
                           Map<VariantKey, String> failures) {

    public ContextPlans {
        roots = Map.copyOf(roots);
        plans = Map.copyOf(plans);
        failures = Map.copyOf(failures);
    }

    /**
     * @throws CaseDefinitionException when the root's configuration is missing or broken, the case names an unknown
     *                                 variant, its variant can't start, or it verifies a bean the variant neither
     *                                 mocks nor spies
     */
    public VariantPlan planFor(YamlCase yamlCase) {
        String where = yamlCase.where();
        RootConfiguration root = roots.get(yamlCase.source().root());
        if (root == null) {
            throw new IllegalStateException(where + ": glob root " + yamlCase.source().root() + " was not loaded");
        }
        if (root.failure() != null) {
            throw new CaseDefinitionException(where + ": " + root.failure());
        }
        if (root.context() == null) {
            throw new CaseDefinitionException(where + ": http and bean cases need " + root.root()
                    + RootConfiguration.CONTEXT_FILE + ", which does not exist");
        }
        if (yamlCase.context() != null && !root.context().variants().containsKey(yamlCase.context())) {
            throw new CaseDefinitionException(where + ": unknown context variant '" + yamlCase.context() + "'; "
                    + root.context().label() + " declares " + new TreeSet<>(root.context().variants().keySet()));
        }
        VariantKey key = new VariantKey(root.root(), yamlCase.context());
        String failure = failures.get(key);
        if (failure != null) {
            throw new CaseDefinitionException(where + ": " + failure);
        }
        VariantPlan plan = plans.get(key);
        yamlCase.steps().forEach(step -> checkVerifiedBeans(plan, step, where));
        return plan;
    }

    private static void checkVerifiedBeans(VariantPlan plan, Step step, String where) {
        for (VerifyCheck check : step.expect().verify()) {
            if (check instanceof VerifyCheck.OnMock onMock && onMock.target() == VerifyCheck.MockTarget.BEAN
                    && !plan.mocks().containsKey(onMock.name()) && !plan.spies().containsKey(onMock.name())) {
                throw new CaseDefinitionException(where + ", step '" + step.name() + "': verify on bean '"
                        + onMock.name() + "', which " + plan.key().describe() + " neither mocks nor spies; add it to"
                        + " `spies` (or `spyBeans` in " + plan.context().label() + ") to verify the real bean");
            }
        }
    }
}
