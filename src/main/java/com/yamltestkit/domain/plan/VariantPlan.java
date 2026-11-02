package com.yamltestkit.domain.plan;

import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.SpyBean;
import com.yamltestkit.domain.model.ValueBean;

import java.util.Map;
import java.util.Set;

/**
 * Everything needed to start one context variant, known before it starts: the merged properties and the beans
 * replaced by name. A bean referenced only by a case or {@code _caseDefaults.yaml} is a {@link MockBean} /
 * {@link SpyBean} without type and boot-time stubs.
 *
 * @param properties  base properties overridden by the variant's, {@code ${wiremock.*}} placeholders unresolved
 * @param mocks       mocked beans by name
 * @param spies       spied beans by name
 * @param values      value beans by name
 * @param references  every file that references each replaced bean, for error messages
 * @param defaultsOnly the mocked or spied beans that only {@code _caseDefaults.yaml} names: skipped, with their
 *                     {@code _caseDefaults.yaml} stubs, when the variant's context has no such bean
 * @param loggers     the loggers named by the {@code expect.logs} entries of the variant's cases, captured from the
 *                    moment its context starts
 */
public record VariantPlan(
        VariantKey key,
        RootConfiguration root,
        ContextFile context,
        String application,
        Map<String, String> properties,
        Map<String, MockBean> mocks,
        Map<String, SpyBean> spies,
        Map<String, ValueBean> values,
        Map<String, Set<String>> references,
        Set<String> defaultsOnly,
        Set<String> loggers
) {

    public VariantPlan {
        properties = Map.copyOf(properties);
        mocks = Map.copyOf(mocks);
        spies = Map.copyOf(spies);
        values = Map.copyOf(values);
        references = Map.copyOf(references);
        defaultsOnly = Set.copyOf(defaultsOnly);
        loggers = Set.copyOf(loggers);
    }

    /** The files that reference a bean, for "unknown bean" messages. */
    public Set<String> referencesOf(String beanName) {
        return references.getOrDefault(beanName, Set.of());
    }
}
