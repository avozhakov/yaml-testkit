package com.yamltestkit.domain.plan;

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.yamltestkit.domain.model.BeanEntry;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CallCount;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseFile;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.ContextVariant;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.LogExpectation;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.SpyBean;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.ValueBean;
import com.yamltestkit.domain.model.VerifyCheck;
import com.yamltestkit.domain.model.YamlCase;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextPlannerTest {

    private static final String ROOT = "cases/";
    private static final String CONTEXT_LABEL = "cases/_context.yaml";
    private static final String DEFAULTS_LABEL = "cases/_defaults.yaml";
    private static final String APPLICATION = "com.example.App";
    private static final CaseSource A_FILE = source("a.yaml");
    private static final CaseSource B_FILE = source("b.yaml");
    private static final Stub BOOT_STUB = new Stub(new MethodRef("findAll", null), null,
            new StubAnswer.Returns(IntNode.valueOf(1)));
    private static final Stub VARIANT_STUB = new Stub(new MethodRef("findAll", null), null,
            new StubAnswer.Returns(IntNode.valueOf(2)));
    private static final MockBean FEED_REPOSITORY = new MockBean("feedRepository", null, false, List.of(BOOT_STUB));
    private static final ValueBean EXCHANGE_SOURCES = new ValueBean("exchangeSources", "com.example.Sources",
            IntNode.valueOf(0), true);
    private static final ContextVariant BASE = new ContextVariant(Map.of("mode", "base", "url", "x"),
            List.of(FEED_REPOSITORY), List.of(new SpyBean("dataLoggingService", List.of())),
            List.of(EXCHANGE_SOURCES));
    private static final ContextVariant ECO = new ContextVariant(Map.of("mode", "eco"),
            List.of(new MockBean("feedRepository", null, false, List.of(VARIANT_STUB))), List.of(), List.of());
    private static final ContextFile CONTEXT = new ContextFile(CONTEXT_LABEL, APPLICATION, List.of(), BASE,
            Map.of("eco", ECO), List.of("partner"), List.of(), 0);
    private static final StubSet DEFAULTS = new StubSet(List.of(), List.of(new BeanEntry("auditService", List.of())),
            List.of());

    @Test
    void plan_whenCasesMockBeans_shouldMockThemForEveryCaseOfTheirVariantOnly() {
        YamlCase defaultCase = beanCase("a", A_FILE, null, mocks("loggerClient"), Expect.NONE);
        YamlCase ecoCase = beanCase("b", B_FILE, "eco", mocks("ebayClient"), Expect.NONE);

        ContextPlans result = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(defaultCase, ecoCase));

        VariantPlan defaultPlan = result.plans().get(new VariantKey(ROOT, null));
        assertThat(defaultPlan.mocks()).containsOnlyKeys("feedRepository", "loggerClient");
        assertThat(defaultPlan.mocks().get("loggerClient")).isEqualTo(new MockBean("loggerClient", null, false,
                List.of()));
        assertThat(defaultPlan.spies()).containsOnlyKeys("dataLoggingService", "auditService");
        assertThat(defaultPlan.values()).containsExactly(Map.entry("exchangeSources", EXCHANGE_SOURCES));
        assertThat(defaultPlan.referencesOf("loggerClient")).containsExactly("cases/a.yaml");
        assertThat(defaultPlan.referencesOf("auditService")).containsExactly(DEFAULTS_LABEL);
        assertThat(result.plans().get(new VariantKey(ROOT, "eco")).mocks())
                .containsOnlyKeys("feedRepository", "ebayClient");
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void plan_whenOnlyTheDefaultsFileNamesABean_shouldMarkItDefaultsOnlyInTheVariantsWhereNoCaseNamesIt() {
        YamlCase ecoCase = beanCase("b", B_FILE, "eco", spies("auditService"), Expect.NONE);

        ContextPlans result = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(ecoCase));

        assertThat(result.plans().get(new VariantKey(ROOT, null)).defaultsOnly()).containsExactly("auditService");
        assertThat(result.plans().get(new VariantKey(ROOT, "eco")).defaultsOnly()).isEmpty();
    }

    @Test
    void plan_whenAVariantRedeclaresEntries_shouldLetTheVariantWinByKeyAndName() {
        ContextPlans result = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), List.of());

        VariantPlan eco = result.plans().get(new VariantKey(ROOT, "eco"));
        assertThat(eco.properties()).containsExactlyInAnyOrderEntriesOf(Map.of("mode", "eco", "url", "x"));
        assertThat(eco.mocks().get("feedRepository").stubs()).containsExactly(VARIANT_STUB);
        assertThat(result.plans().get(new VariantKey(ROOT, null)).mocks().get("feedRepository"))
                .isEqualTo(FEED_REPOSITORY);
    }

    @Test
    void plan_whenAStepMocksABean_shouldMockItForTheVariant() {
        Step step = new Step("reload", beanCall(), mocks("stepOnly"), Expect.NONE);
        YamlCase stepCase = new YamlCase("a", A_FILE, List.of(step), StubSet.EMPTY, StubSet.EMPTY, null, false);

        ContextPlans result = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(stepCase));

        assertThat(result.plans().get(new VariantKey(ROOT, null)).mocks()).containsKey("stepOnly");
    }

    @Test
    void plan_whenCasesExpectLogs_shouldCollectTheirLoggersForTheirVariantOnly() {
        YamlCase defaultCase = beanCase("a", A_FILE, null, StubSet.EMPTY, logging("com.example.FeedLoader"));
        YamlCase ecoCase = beanCase("b", B_FILE, "eco", StubSet.EMPTY, logging("com.example.Pricing"));

        ContextPlans result = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(defaultCase, ecoCase));

        assertThat(result.plans().get(new VariantKey(ROOT, null)).loggers())
                .containsExactly("com.example.FeedLoader");
        assertThat(result.plans().get(new VariantKey(ROOT, "eco")).loggers()).containsExactly("com.example.Pricing");
    }

    @Test
    void planFor_whenABeanIsMockedAndSpiedInOneVariant_shouldFailEveryCaseOfTheVariantNamingBothFiles() {
        YamlCase mocking = beanCase("a", A_FILE, null, mocks("auditService"), Expect.NONE);
        YamlCase other = beanCase("b", B_FILE, null, StubSet.EMPTY, Expect.NONE);
        ContextPlans plans = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(mocking, other));

        assertThatThrownBy(() -> plans.planFor(other))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage("cases/b.yaml, case 'b': bean 'auditService' is both mocked (in cases/a.yaml) and spied"
                        + " (in cases/_defaults.yaml) in cases/ variant 'default'; a bean is replaced the same way for"
                        + " every case of a variant, so move one of these cases to another variant");
        assertThat(plans.plans()).containsOnlyKeys(new VariantKey(ROOT, "eco"));
    }

    @Test
    void plan_whenAValueBeanIsAlsoMocked_shouldFailTheVariant() {
        YamlCase mocking = beanCase("a", A_FILE, null, mocks("exchangeSources"), Expect.NONE);

        ContextPlans result = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(mocking));

        assertThat(result.failures().get(new VariantKey(ROOT, null)))
                .startsWith("bean 'exchangeSources' is both a value bean (in cases/_context.yaml) and mocked (in"
                        + " cases/a.yaml)");
    }

    @Test
    void plan_whenTheContextFileHasNoApplication_shouldFailEveryVariant() {
        ContextFile withoutApplication = new ContextFile(CONTEXT_LABEL, null, List.of(), BASE, Map.of(), List.of(),
                List.of(), 0);

        ContextPlans result = ContextPlanner.plan(Map.of(ROOT, root(withoutApplication)), List.of());

        assertThat(result.failures()).containsExactly(Map.entry(new VariantKey(ROOT, null),
                "cases/_context.yaml has no `application`; http and bean cases need the Spring Boot application"
                        + " class"));
    }

    @Test
    void planFor_whenTheCaseNamesAnUnknownVariant_shouldFailNamingTheDeclaredOnes() {
        YamlCase typo = beanCase("a", A_FILE, "ebay-of", StubSet.EMPTY, Expect.NONE);
        ContextPlans plans = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(typo));

        assertThatThrownBy(() -> plans.planFor(typo))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage("cases/a.yaml, case 'a': unknown context variant 'ebay-of'; cases/_context.yaml declares"
                        + " [eco]");
    }

    @Test
    void planFor_whenTheCaseVerifiesABeanThatIsNeitherMockedNorSpied_shouldFailSuggestingSpies() {
        YamlCase verifying = beanCase("a", A_FILE, null, StubSet.EMPTY, verifying("campaignClient"));
        ContextPlans plans = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(verifying));

        assertThatThrownBy(() -> plans.planFor(verifying))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage("cases/a.yaml, case 'a', step 'main': verify on bean 'campaignClient', which cases/"
                        + " variant 'default' neither mocks nor spies; add it to `spies` (or `spyBeans` in"
                        + " cases/_context.yaml) to verify the real bean");
    }

    @Test
    void planFor_whenTheCaseVerifiesASpiedBean_shouldReturnTheVariantPlan() {
        YamlCase verifying = beanCase("a", A_FILE, null, StubSet.EMPTY, verifying("dataLoggingService"));
        ContextPlans plans = ContextPlanner.plan(Map.of(ROOT, root(CONTEXT)), files(verifying));

        VariantPlan result = plans.planFor(verifying);

        assertThat(result.key()).isEqualTo(new VariantKey(ROOT, null));
        assertThat(result.application()).isEqualTo(APPLICATION);
    }

    @Test
    void planFor_whenTheRootHasNoContextFile_shouldFailNamingTheMissingFile() {
        YamlCase yamlCase = beanCase("a", A_FILE, null, StubSet.EMPTY, Expect.NONE);
        ContextPlans plans = ContextPlanner.plan(Map.of(ROOT, new RootConfiguration(ROOT, null, StubSet.EMPTY,
                DEFAULTS_LABEL, null)), files(yamlCase));

        assertThatThrownBy(() -> plans.planFor(yamlCase))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage("cases/a.yaml, case 'a': http and bean cases need cases/_context.yaml, which does not"
                        + " exist");
    }

    @Test
    void planFor_whenTheRootConfigurationIsBroken_shouldFailWithItsReason() {
        YamlCase yamlCase = beanCase("a", A_FILE, null, StubSet.EMPTY, Expect.NONE);
        ContextPlans plans = ContextPlanner.plan(Map.of(ROOT, new RootConfiguration(ROOT, null, StubSet.EMPTY,
                DEFAULTS_LABEL, "cases/_context.yaml: invalid YAML")), files(yamlCase));

        assertThatThrownBy(() -> plans.planFor(yamlCase))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage("cases/a.yaml, case 'a': cases/_context.yaml: invalid YAML");
    }

    private static RootConfiguration root(ContextFile context) {
        return new RootConfiguration(ROOT, context, DEFAULTS, DEFAULTS_LABEL, null);
    }

    private static StubSet mocks(String bean) {
        return new StubSet(List.of(new BeanEntry(bean, List.of())), List.of(), List.of());
    }

    private static StubSet spies(String bean) {
        return new StubSet(List.of(), List.of(new BeanEntry(bean, List.of())), List.of());
    }

    private static Expect verifying(String bean) {
        VerifyCheck check = new VerifyCheck.OnMock(VerifyCheck.MockTarget.BEAN, bean, new MethodRef("send", null),
                null, null, CallCount.AT_LEAST_ONCE, null, null, List.of());
        return new Expect(null, Map.of(), null, null, List.of(), MissingNode.getInstance(), null, List.of(check),
                List.of(), null, null);
    }

    private static Expect logging(String logger) {
        return new Expect(null, Map.of(), null, null, List.of(), MissingNode.getInstance(), null, List.of(),
                List.of(new LogExpectation(logger, LogExpectation.LogLevel.WARN, null, null, false)), null, null);
    }

    private static Call beanCall() {
        return new Call.Bean("feedCache", new MethodRef("reload", null), List.of());
    }

    private static YamlCase beanCase(String name, CaseSource source, @Nullable String variant, StubSet stubs,
                                     Expect expect) {
        return new YamlCase(name, source, List.of(new Step(Step.IMPLICIT_NAME, beanCall(), StubSet.EMPTY, expect)),
                StubSet.EMPTY, stubs, variant, false);
    }

    private static List<CaseFile> files(YamlCase... cases) {
        return List.of(cases).stream().map(yamlCase -> new CaseFile(yamlCase.source(), List.of(yamlCase))).toList();
    }

    private static CaseSource source(String relativePath) {
        return new CaseSource(URI.create("classpath:/cases/" + relativePath), ROOT, relativePath);
    }
}
