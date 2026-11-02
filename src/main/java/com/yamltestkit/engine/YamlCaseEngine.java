package com.yamltestkit.engine;

import com.yamltestkit.convert.ValueConverter;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.BrokenCase;
import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseEntry;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.LogExpectation;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.infrastructure.logback.LogCapture;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import com.yamltestkit.infrastructure.spring.CaseContext;
import com.yamltestkit.infrastructure.spring.SpringCaseLifecycle;
import com.yamltestkit.infrastructure.yaml.CaseFileLoader;
import com.yamltestkit.infrastructure.yaml.CaseResourceLocator;
import com.yamltestkit.infrastructure.yaml.RootConfigurationLoader;
import com.yamltestkit.infrastructure.yaml.SchemaValidator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The engine behind {@code YamlCaseSuite}: load the case files of the suite's globs, plan which cases run, run one
 * case. Negative tests call this API directly to check the failure of a broken case. Closing the engine closes the
 * application contexts and WireMock servers its cases started, the log capture and the step executors.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class YamlCaseEngine implements AutoCloseable {

    private final SuiteSettings settings;
    private final CaseResourceLocator locator;
    private final CaseFileLoader loader;
    private final Map<CallKind, StepExecutor> executors;
    private final SpringCaseLifecycle springCases;
    private final LogCapture logs;
    private final AntPathMatcher filterMatcher = new AntPathMatcher();

    /**
     * Reads {@code _context.yaml} / {@code _caseDefaults.yaml} of each glob root up front: their {@code converters}
     * join the converter chain, after those of {@code @YamlCases}.
     */
    public static YamlCaseEngine create(SuiteSettings settings) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        SchemaValidator schemaValidator = new SchemaValidator();
        Map<String, RootConfiguration> roots = new RootConfigurationLoader(new DefaultResourceLoader(classLoader),
                schemaValidator).load(settings.globs());
        ValueConverters converters = ValueConverters.create(converters(settings, roots));
        LogCapture logs = LogCapture.create();
        return new YamlCaseEngine(settings, new CaseResourceLocator(classLoader), new CaseFileLoader(schemaValidator),
                executors(converters, EffectChecks.create(converters, logs)),
                SpringCaseLifecycle.create(converters, roots, logs), logs);
    }

    /**
     * @throws IllegalStateException when the suite's globs match no case file
     */
    public List<CaseFile> load() {
        return locator.locate(settings.globs()).stream().map(loader::load).toList();
    }

    /**
     * Keeps the cases whose calls are all of the suite's kinds and whose path matches {@code yamlit.filter}. The
     * context variants are planned from all of {@code files}, so a case behaves the same whether it runs alone or
     * with the others.
     */
    public CasePlan plan(List<CaseFile> files) {
        springCases.prepare(files);
        return new CasePlan(files.stream()
                .map(file -> file.withCases(file.cases().stream().filter(this::isSelected).toList()))
                .filter(file -> !file.cases().isEmpty())
                .toList());
    }

    public CaseResult run(CaseEntry entry) {
        return switch (entry) {
            case BrokenCase broken -> CaseResult.failed(broken.casePath(), new CaseDefinitionException(broken.reason()));
            case YamlCase yamlCase -> runSteps(yamlCase);
        };
    }

    @Override
    public void close() {
        try {
            springCases.close();
        } finally {
            try {
                logs.close();
            } finally {
                executors.values().forEach(StepExecutor::close);
            }
        }
    }

    private CaseResult runSteps(YamlCase yamlCase) {
        CaseRun run = new CaseRun(yamlCase);
        try (CaseContext context = needsContext(yamlCase) ? springCases.open(yamlCase, run.stubErrors()) : null) {
            if (context != null) {
                run.attach(context);
            } else {
                startUnitLogCapture(yamlCase);
            }
            for (Step step : yamlCase.steps()) {
                if (context != null) {
                    springCases.applyStepStubs(context, step, run.stubErrors(), run.where(step));
                }
                StepPolling.execute(executors.get(step.call().kind()), run, step);
            }
            return CaseResult.passed(yamlCase.casePath());
        } catch (Exception | AssertionError e) {
            // Not swallowed: the failure becomes the case result, which the suite rethrows and negative tests inspect.
            return CaseResult.failed(yamlCase.casePath(), e);
        }
    }

    /**
     * A case without a context captures the log events of the loggers its {@code expect.logs} entries name from its
     * start; an {@code http} / {@code bean} case gets that from its context's lifecycle.
     */
    private void startUnitLogCapture(YamlCase yamlCase) {
        logs.clear();
        logs.attach(yamlCase.steps().stream()
                .flatMap(step -> step.expect().logs().stream())
                .map(LogExpectation::logger)
                .toList());
    }

    private static boolean needsContext(YamlCase yamlCase) {
        return yamlCase.kinds().contains(CallKind.HTTP) || yamlCase.kinds().contains(CallKind.BEAN);
    }

    private boolean isSelected(CaseEntry entry) {
        boolean kindSelected = !(entry instanceof YamlCase yamlCase) || settings.kinds().containsAll(yamlCase.kinds());
        return kindSelected && (settings.filter() == null || filterMatcher.match(settings.filter(), entry.casePath()));
    }

    /** The converters of {@code @YamlCases}, then those of each loaded {@code _context.yaml}, without repeats. */
    private static List<Class<? extends ValueConverter>> converters(SuiteSettings settings,
                                                                    Map<String, RootConfiguration> roots) {
        Set<Class<? extends ValueConverter>> converters = new LinkedHashSet<>(settings.converters());
        roots.values().stream()
                .filter(root -> root.context() != null)
                .flatMap(root -> root.context().converters().stream()
                        .map(name -> TypeNames.load(name, root.context().label() + ": converters")))
                .forEach(type -> converters.add(type.asSubclass(ValueConverter.class)));
        return new ArrayList<>(converters);
    }

    private static Map<CallKind, StepExecutor> executors(ValueConverters converters, EffectChecks effects) {
        Map<CallKind, StepExecutor> executors = new EnumMap<>(CallKind.class);
        executors.put(CallKind.HTTP, HttpStepExecutor.create(effects));
        executors.put(CallKind.BEAN, BeanStepExecutor.create(converters, effects));
        executors.put(CallKind.CLASS, UnitStepExecutor.create(converters, effects));
        return executors;
    }
}
