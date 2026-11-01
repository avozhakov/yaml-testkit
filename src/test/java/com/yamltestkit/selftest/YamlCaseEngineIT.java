package com.yamltestkit.selftest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.helpers.NOPAppender;
import ch.qos.logback.core.read.ListAppender;
import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.domain.model.CaseEntry;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.engine.CasePlan;
import com.yamltestkit.engine.CaseResult;
import com.yamltestkit.engine.SuiteSettings;
import com.yamltestkit.engine.YamlCaseEngine;
import com.yamltestkit.selftest.app.StartupInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the self-test cases through {@link YamlCaseEngine} directly, for what a case file can't check about its own
 * run: how many contexts the run starts, a file run alone, and a logging configuration changed under the cases.
 * Contexts are counted through {@link StartupInfo#created()}, one instance per started fixture context.
 */
class YamlCaseEngineIT {

    private static final List<String> GLOBS = List.of("com/yamltestkit/selftest/cases/**");
    private static final Set<CallKind> KINDS = Set.of(CallKind.HTTP, CallKind.BEAN);
    private static final String FIXTURE_LOGGER = "com.yamltestkit.selftest.app";
    private static final String NO_LEAK_CASES = "verify/no-leak.yaml/*";
    private static final String LOG_CASES = "logs/log-events.yaml/*";
    /** Cases of the default, eco and no-retry variants, and fresh cases of the default and unmappable-feed ones. */
    private static final String CONTEXT_CASES = "context/**";

    /**
     * A variant whose cases are all fresh (unmappable-feed) never starts a shared context. The context cases cover every
     * kind of variant use at a fraction of the whole suite's run time.
     */
    @Test
    void run_whenCasesOfSeveralVariantsRun_shouldStartOneContextPerSharedVariantAndOnePerFreshCase() {
        try (YamlCaseEngine engine = engine(CONTEXT_CASES)) {
            CasePlan plan = engine.plan(engine.load());
            List<YamlCase> cases = yamlCases(plan);
            long sharedVariants = cases.stream().filter(yamlCase -> !yamlCase.freshContext())
                    .map(YamlCase::context).distinct().count();
            long freshCases = cases.stream().filter(YamlCase::freshContext).count();
            int startedBefore = StartupInfo.created();

            List<CaseResult> results = runAll(engine, plan);

            assertThat(failures(results)).isEmpty();
            assertThat(sharedVariants).isGreaterThan(1);
            assertThat(freshCases).isGreaterThan(1);
            assertThat(StartupInfo.created() - startedBefore).isEqualTo(sharedVariants + freshCases);
        }
    }

    /** The first case starts the default variant's context and the second the eco one's, so both see a fresh start. */
    @Test
    void run_whenTheNoLeakFileRunsAlone_shouldNotSeeTheStartupCallsOfTheContextsItStarts() {
        try (YamlCaseEngine engine = engine(NO_LEAK_CASES)) {
            CasePlan plan = engine.plan(engine.load());
            int startedBefore = StartupInfo.created();

            List<CaseResult> results = runAll(engine, plan);

            assertThat(results).hasSize(5);
            assertThat(failures(results)).isEmpty();
            assertThat(StartupInfo.created() - startedBefore).isEqualTo(2);
        }
    }

    /**
     * Imitates a service's logback-test.xml, which declares its root package non-additive with only a no-op appender.
     * The logger is changed after the context has started, since a context start re-initialises Logback; a probe on
     * the root logger shows the fixture's events no longer reach it.
     */
    @Test
    void run_whenTheFixtureLoggerIsNotAdditive_shouldStillCaptureItsEvents() {
        Logger fixtureLogger = (Logger) LoggerFactory.getLogger(FIXTURE_LOGGER);
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        NOPAppender<ILoggingEvent> noOp = startedAppender(new NOPAppender<>());
        ListAppender<ILoggingEvent> rootProbe = startedAppender(new ListAppender<>());
        try (YamlCaseEngine engine = engine(LOG_CASES)) {
            List<CaseEntry> cases = entries(engine.plan(engine.load()));
            assertThat(engine.run(cases.getFirst()).isPassed()).isTrue();
            fixtureLogger.addAppender(noOp);
            fixtureLogger.setAdditive(false);
            rootLogger.addAppender(rootProbe);

            List<CaseResult> results = cases.stream().map(engine::run).toList();

            assertThat(failures(results)).isEmpty();
            assertThat(rootProbe.list).noneMatch(event -> event.getLoggerName().startsWith(FIXTURE_LOGGER));
        } finally {
            rootLogger.detachAppender(rootProbe);
            fixtureLogger.detachAppender(noOp);
            fixtureLogger.setAdditive(true);
        }
    }

    private static YamlCaseEngine engine(String filter) {
        return YamlCaseEngine.create(new SuiteSettings(GLOBS, KINDS, List.of(), filter));
    }

    private static List<CaseResult> runAll(YamlCaseEngine engine, CasePlan plan) {
        return entries(plan).stream().map(engine::run).toList();
    }

    private static List<CaseEntry> entries(CasePlan plan) {
        return plan.files().stream().flatMap(file -> file.cases().stream()).toList();
    }

    private static List<YamlCase> yamlCases(CasePlan plan) {
        return entries(plan).stream()
                .filter(YamlCase.class::isInstance)
                .map(YamlCase.class::cast)
                .toList();
    }

    /** Failed cases with their failures, so an assertion on them shows why each failed. */
    private static List<String> failures(List<CaseResult> results) {
        return results.stream()
                .filter(result -> !result.isPassed())
                .map(result -> result.casePath() + ": " + Objects.requireNonNull(result.failure()).getMessage())
                .toList();
    }

    private static <A extends Appender<ILoggingEvent>> A startedAppender(A appender) {
        appender.setName("yaml-case-engine-it-" + appender.getClass().getSimpleName());
        appender.start();
        return appender;
    }
}
