package com.yamltestkit.domain.plan;

import com.yamltestkit.domain.model.BeanEntry;
import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.ContextVariant;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.SpyBean;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.ValueBean;
import com.yamltestkit.domain.model.YamlCase;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Plans every variant of every glob root before any context starts. The beans a variant mocks or spies are those of
 * {@code _context.yaml} (base and variant entries, the variant's winning by name) plus every bean that
 * {@code _caseDefaults.yaml} or any loaded {@code http} / {@code bean} case of the variant names under {@code mocks} /
 * {@code spies}: once one case mocks a bean, it is a mock for the whole variant. All loaded cases count, not only the
 * selected ones, so a case behaves the same whether it runs alone or with the others. A bean both mocked and spied,
 * or replaced by a value bean and also mocked or spied, fails the variant. A bean only {@code _caseDefaults.yaml}
 * names is marked as such, so a variant whose context has no such bean skips it instead of failing to start. The
 * loggers the variant's cases name in {@code expect.logs} are collected too, so their events are captured from the
 * moment the context starts.
 */
public final class ContextPlanner {

    private ContextPlanner() {
    }

    public static ContextPlans plan(Map<String, RootConfiguration> roots, List<CaseFile> files) {
        Map<VariantKey, VariantPlan> plans = new HashMap<>();
        Map<VariantKey, String> failures = new HashMap<>();
        for (RootConfiguration root : roots.values()) {
            ContextFile context = root.context();
            if (context == null) {
                continue;
            }
            List<YamlCase> cases = springCases(files, root.root());
            Map<String, ContextVariant> variants = new LinkedHashMap<>();
            variants.put(null, ContextVariant.EMPTY);
            variants.putAll(context.variants());
            variants.forEach((name, variant) -> {
                VariantKey key = new VariantKey(root.root(), name);
                Builder builder = new Builder(key, root, context, variant, cases);
                String failure = builder.failure();
                if (failure == null) {
                    plans.put(key, builder.build());
                } else {
                    failures.put(key, failure);
                }
            });
        }
        return new ContextPlans(roots, plans, failures);
    }

    private static List<YamlCase> springCases(List<CaseFile> files, String root) {
        return files.stream()
                .filter(file -> file.source().root().equals(root))
                .flatMap(file -> file.cases().stream())
                .filter(YamlCase.class::isInstance)
                .map(YamlCase.class::cast)
                .filter(yamlCase -> yamlCase.kinds().contains(CallKind.HTTP) || yamlCase.kinds().contains(CallKind.BEAN))
                .toList();
    }

    /** Collects one variant's properties and bean entries with the files referencing each bean. */
    private static final class Builder {

        private final VariantKey key;
        private final RootConfiguration root;
        private final ContextFile context;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private final Map<String, MockBean> mocks = new LinkedHashMap<>();
        private final Map<String, SpyBean> spies = new LinkedHashMap<>();
        private final Map<String, ValueBean> values = new LinkedHashMap<>();
        private final Map<String, Set<String>> mockReferences = new LinkedHashMap<>();
        private final Map<String, Set<String>> spyReferences = new LinkedHashMap<>();
        private final Map<String, Set<String>> valueReferences = new LinkedHashMap<>();
        private final Set<String> loggers = new TreeSet<>();

        private Builder(VariantKey key, RootConfiguration root, ContextFile context, ContextVariant variant,
                        List<YamlCase> cases) {
            this.key = key;
            this.root = root;
            this.context = context;
            addContextEntries(context.base());
            addContextEntries(variant);
            addStubSet(root.defaults(), root.defaultsLabel());
            cases.stream()
                    .filter(yamlCase -> Objects.equals(yamlCase.context(), key.variant()))
                    .forEach(this::addCase);
        }

        private void addContextEntries(ContextVariant entries) {
            properties.putAll(entries.properties());
            entries.mockBeans().forEach(mock -> {
                mocks.put(mock.name(), mock);
                reference(mockReferences, mock.name(), context.label());
            });
            entries.spyBeans().forEach(spy -> {
                spies.put(spy.name(), spy);
                reference(spyReferences, spy.name(), context.label());
            });
            entries.valueBeans().forEach(value -> {
                values.put(value.name(), value);
                reference(valueReferences, value.name(), context.label());
            });
        }

        private void addCase(YamlCase yamlCase) {
            String label = yamlCase.source().label();
            addStubSet(yamlCase.fileDefaults(), label);
            addStubSet(yamlCase.stubs(), label);
            yamlCase.steps().stream().map(Step::stubs).forEach(stubs -> addStubSet(stubs, label));
            yamlCase.steps().forEach(step -> step.expect().logs().forEach(log -> loggers.add(log.logger())));
        }

        private void addStubSet(StubSet stubs, String label) {
            stubs.mocks().stream().map(BeanEntry::bean).forEach(name -> {
                mocks.putIfAbsent(name, new MockBean(name, null, false, List.of()));
                reference(mockReferences, name, label);
            });
            stubs.spies().stream().map(BeanEntry::bean).forEach(name -> {
                spies.putIfAbsent(name, new SpyBean(name, List.of()));
                reference(spyReferences, name, label);
            });
        }

        private @Nullable String failure() {
            if (context.application() == null) {
                return context.label() + " has no `application`; http and bean cases need the Spring Boot"
                        + " application class";
            }
            for (String name : mockReferences.keySet()) {
                if (spyReferences.containsKey(name)) {
                    return conflict(name, "mocked", mockReferences, "spied", spyReferences);
                }
            }
            for (String name : valueReferences.keySet()) {
                if (mockReferences.containsKey(name)) {
                    return conflict(name, "a value bean", valueReferences, "mocked", mockReferences);
                }
                if (spyReferences.containsKey(name)) {
                    return conflict(name, "a value bean", valueReferences, "spied", spyReferences);
                }
            }
            return null;
        }

        private String conflict(String name, String first, Map<String, Set<String>> firstReferences, String second,
                                Map<String, Set<String>> secondReferences) {
            return "bean '" + name + "' is both " + first + " (in " + String.join(", ", firstReferences.get(name))
                    + ") and " + second + " (in " + String.join(", ", secondReferences.get(name)) + ") in "
                    + key.describe() + "; a bean is replaced the same way for every case of a variant, so move one"
                    + " of these cases to another variant";
        }

        private VariantPlan build() {
            Map<String, Set<String>> references = new HashMap<>();
            Stream.of(mockReferences, spyReferences, valueReferences).forEach(byName -> byName.forEach(
                    (name, files) -> references.computeIfAbsent(name, ignored -> new TreeSet<>()).addAll(files)));
            Set<String> defaultsOnly = new TreeSet<>();
            Stream.of(mockReferences, spyReferences).forEach(byName -> byName.forEach((name, files) -> {
                if (files.equals(Set.of(root.defaultsLabel()))) {
                    defaultsOnly.add(name);
                }
            }));
            return new VariantPlan(key, root, context, context.application(), properties, mocks, spies, values,
                    references, defaultsOnly, loggers);
        }

        private static void reference(Map<String, Set<String>> references, String name, String label) {
            references.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(label);
        }
    }
}
