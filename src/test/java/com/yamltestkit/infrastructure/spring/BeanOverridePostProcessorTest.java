package com.yamltestkit.infrastructure.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.ContextVariant;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.SpyBean;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.ValueBean;
import com.yamltestkit.domain.plan.VariantKey;
import com.yamltestkit.domain.plan.VariantPlan;
import com.yamltestkit.infrastructure.mockito.StubApplier;
import com.yamltestkit.infrastructure.mockito.StubErrors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericTypeAwareAutowireCandidateResolver;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BeanOverridePostProcessorTest {

    private static final String LABEL = "cases/_context.yaml";
    private static final Stub GET_STUB = new Stub(new MethodRef("get", null), null,
            new StubAnswer.Returns(JsonNodeFactory.instance.objectNode()));
    private static final Stub LABEL_STUB = new Stub(new MethodRef("label", null), null,
            new StubAnswer.Returns(IntNode.valueOf(1)));
    private static final Type BLOB_SUPPLIER_TYPE = ResolvableType.forClassWithGenerics(Supplier.class,
            ResolvableType.forClassWithGenerics(Map.class, String.class, byte[].class)).getType();
    private static final JsonNode EMPTY_SOURCES = JsonNodeFactory.instance.objectNode().putArray("sources");
    private static final Sources SOURCES = new Sources(List.of());

    private ContextBeans mockBeans;
    private StubApplier mockStubs;
    private ValueConverters mockConverters;
    private StubErrors startupErrors;
    private DefaultListableBeanFactory factory;

    @BeforeEach
    void setUp() {
        mockBeans = mock(ContextBeans.class);
        mockStubs = mock(StubApplier.class);
        mockConverters = mock(ValueConverters.class);
        startupErrors = new StubErrors();
        when(mockBeans.startupErrors()).thenReturn(startupErrors);
        factory = new DefaultListableBeanFactory();
        factory.setAutowireCandidateResolver(new GenericTypeAwareAutowireCandidateResolver());
        factory.registerBeanDefinition("config", new RootBeanDefinition(Config.class));
        factory.registerBeanDefinition("blobSupplier", factoryMethod("blobSupplier", true));
        factory.registerBeanDefinition("nameSupplier", factoryMethod("nameSupplier", false));
        RootBeanDefinition consumer = new RootBeanDefinition(BlobConsumer.class);
        consumer.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        factory.registerBeanDefinition("blobConsumer", consumer);
    }

    @Test
    void postProcessBeanFactory_whenABeanIsMocked_shouldInjectAMockByItsGenericTypeWithoutCreatingTheRealBean() {
        underTest(Map.of("blobSupplier", new MockBean("blobSupplier", null, false, List.of(GET_STUB))), Map.of(),
                Map.of()).postProcessBeanFactory(factory);

        BlobConsumer result = factory.getBean(BlobConsumer.class);

        assertThat(Mockito.mockingDetails(result.supplier()).isMock()).isTrue();
        assertThat(factory.getBeanDefinition("blobSupplier").isPrimary()).isTrue();
        verify(mockStubs).apply(same(result.supplier()), eq(BLOB_SUPPLIER_TYPE), eq(List.of(GET_STUB)),
                same(startupErrors), eq(LABEL + ", mockBeans blobSupplier"));
        verify(mockBeans).register("blobSupplier", result.supplier(), BLOB_SUPPLIER_TYPE);
    }

    @Test
    void postProcessBeanFactory_whenAMockedBeanDoesNotExistAndHasAType_shouldCreateAPrimaryMockOfThatType() {
        underTest(Map.of("reportStore", new MockBean("reportStore", ReportStore.class.getName(), true, List.of())),
                Map.of(), Map.of()).postProcessBeanFactory(factory);

        ReportStore result = factory.getBean(ReportStore.class);

        assertThat(Mockito.mockingDetails(result).isMock()).isTrue();
        assertThat(factory.getBeanDefinition("reportStore").isPrimary()).isTrue();
        verify(mockBeans).register("reportStore", result, ReportStore.class);
    }

    @Test
    void postProcessBeanFactory_whenAMockedBeanDoesNotExistAndHasNoType_shouldFailNamingTheReferencingFiles() {
        BeanOverridePostProcessor underTest = underTest(Map.of("campainClient", new MockBean("campainClient", null,
                false, List.of())), Map.of(), Map.of());

        assertThatThrownBy(() -> underTest.postProcessBeanFactory(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cases/ variant 'default': can't mock bean 'campainClient': the context has no bean of"
                        + " that name (referenced by cases/a.yaml, cases/b.yaml); to create it, add `type` to its"
                        + " mockBeans entry in cases/_context.yaml");
    }

    @Test
    void postProcessBeanFactory_whenAValueBeanIsDeclared_shouldReplaceTheBeanWithTheConvertedValue() {
        factory.registerBeanDefinition("sources", new RootBeanDefinition(Sources.class));
        when(mockConverters.fromYaml(EMPTY_SOURCES, Sources.class, LABEL + ", valueBeans sources.value"))
                .thenReturn(SOURCES);

        underTest(Map.of(), Map.of(), Map.of("sources", new ValueBean("sources", Sources.class.getName(),
                EMPTY_SOURCES, true))).postProcessBeanFactory(factory);

        assertThat(factory.getBean(Sources.class)).isSameAs(SOURCES);
        assertThat(factory.getBeanDefinition("sources").isPrimary()).isTrue();
    }

    @Test
    void postProcessBeanFactory_whenABeanIsSpied_shouldWrapTheRealBeanInASpyKeepingRealBehaviour() {
        factory.registerBeanDefinition("auditService", new RootBeanDefinition(AuditService.class));

        underTest(Map.of(), Map.of("auditService", new SpyBean("auditService", List.of(LABEL_STUB))), Map.of())
                .postProcessBeanFactory(factory);
        AuditService result = factory.getBean(AuditService.class);

        assertThat(Mockito.mockingDetails(result).isSpy()).isTrue();
        assertThat(result.label("x")).isEqualTo("audit:x");
        verify(mockStubs).apply(result, AuditService.class, List.of(LABEL_STUB), startupErrors,
                LABEL + ", spyBeans auditService");
        verify(mockBeans).register("auditService", result, AuditService.class);
    }

    @Test
    void postProcessBeanFactory_whenASpiedBeanDoesNotExist_shouldFailNamingTheReferencingFiles() {
        BeanOverridePostProcessor underTest = underTest(Map.of(), Map.of("auditService", new SpyBean("auditService",
                List.of())), Map.of());

        assertThatThrownBy(() -> underTest.postProcessBeanFactory(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cases/ variant 'default': can't spy bean 'auditService': the context has no bean of"
                        + " that name (referenced by cases/a.yaml, cases/b.yaml)");
    }

    @Test
    void postProcessBeanFactory_whenASpiedBeanIsAProxy_shouldFailTheBeanCreationNamingTheBean() {
        RootBeanDefinition proxy = new RootBeanDefinition(Runnable.class);
        proxy.setInstanceSupplier(() -> Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Runnable.class}, (target, method, args) -> null));
        factory.registerBeanDefinition("task", proxy);
        underTest(Map.of(), Map.of("task", new SpyBean("task", List.of())), Map.of()).postProcessBeanFactory(factory);

        assertThatThrownBy(() -> factory.getBean("task"))
                .isInstanceOf(BeanCreationException.class)
                .rootCause()
                .hasMessageStartingWith(LABEL + ", spyBeans task: bean 'task' is a proxy");
        verify(mockBeans, never()).register(eq("task"), any(), any());
    }

    @Test
    void postProcessBeanFactory_whenAFactoryBeanDefinitionIsMocked_shouldMockItsProductType() {
        factory.registerBeanDefinition("reportStore", new RootBeanDefinition(ReportStoreFactory.class));

        underTest(Map.of("reportStore", new MockBean("reportStore", null, false, List.of())), Map.of(), Map.of())
                .postProcessBeanFactory(factory);
        ReportStore result = factory.getBean(ReportStore.class);

        assertThat(Mockito.mockingDetails(result).isMock()).isTrue();
        verify(mockBeans).register("reportStore", result, ReportStore.class);
    }

    @Test
    void postProcessBeanFactory_whenAFactoryBeansProductTypeIsUnknown_shouldFailAskingForType() {
        factory.registerBeanDefinition("anything", new RootBeanDefinition(GenericFactory.class));
        BeanOverridePostProcessor underTest = underTest(Map.of("anything", new MockBean("anything", null, false,
                List.of())), Map.of(), Map.of());

        assertThatThrownBy(() -> underTest.postProcessBeanFactory(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(LABEL + ", mockBeans anything: bean 'anything' is made by a FactoryBean whose product type"
                        + " can't be determined without creating it; add `type` to its mockBeans entry");
    }

    @Test
    void postProcessBeanFactory_whenAFactoryBeansProductTypeIsUnknownAndTypeIsGiven_shouldMockThatType() {
        factory.registerBeanDefinition("anything", new RootBeanDefinition(GenericFactory.class));

        underTest(Map.of("anything", new MockBean("anything", ReportStore.class.getName(), false, List.of())),
                Map.of(), Map.of()).postProcessBeanFactory(factory);

        assertThat(Mockito.mockingDetails(factory.getBean("anything")).getMockCreationSettings().getTypeToMock())
                .isEqualTo(ReportStore.class);
    }

    @Test
    void postProcessBeanFactory_whenAMockedDefinitionHasQualifiersFallbackAndLazyInit_shouldKeepThem()
            throws Exception {
        AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(Qualifier.class, "names");
        RootBeanDefinition original = factoryMethod("qualifiedSupplier", false);
        original.addQualifier(qualifier);
        original.setFallback(true);
        original.setLazyInit(true);
        factory.registerBeanDefinition("qualifiedSupplier", original);

        underTest(Map.of("qualifiedSupplier", new MockBean("qualifiedSupplier", null, false, List.of())), Map.of(),
                Map.of()).postProcessBeanFactory(factory);

        RootBeanDefinition result = (RootBeanDefinition) factory.getBeanDefinition("qualifiedSupplier");
        assertThat(result.getQualifiers()).containsExactly(qualifier);
        assertThat(result.isFallback()).isTrue();
        assertThat(result.isLazyInit()).isTrue();
        assertThat(result.getQualifiedElement()).isEqualTo(Config.class.getMethod("qualifiedSupplier"));
    }

    @Test
    void postProcessBeanFactory_whenAValueBeanReplacesAPrimaryDefinition_shouldStayPrimary() {
        RootBeanDefinition original = new RootBeanDefinition(Sources.class);
        original.setPrimary(true);
        factory.registerBeanDefinition("sources", original);
        when(mockConverters.fromYaml(EMPTY_SOURCES, Sources.class, LABEL + ", valueBeans sources.value"))
                .thenReturn(SOURCES);

        underTest(Map.of(), Map.of(), Map.of("sources", new ValueBean("sources", Sources.class.getName(),
                EMPTY_SOURCES, false))).postProcessBeanFactory(factory);

        assertThat(factory.getBeanDefinition("sources").isPrimary()).isTrue();
        assertThat(factory.getBean("sources")).isSameAs(SOURCES);
    }

    @Test
    void postProcessBeanFactory_whenAMockNamesAnAlias_shouldReplaceTheBeanItNamesAndRecordTheMockUnderTheAlias() {
        factory.registerAlias("nameSupplier", "names");

        underTest(Map.of("names", new MockBean("names", null, false, List.of())), Map.of(), Map.of())
                .postProcessBeanFactory(factory);
        Object result = factory.getBean("names");

        assertThat(Mockito.mockingDetails(result).isMock()).isTrue();
        assertThat(factory.getBean("nameSupplier")).isSameAs(result);
        assertThat(factory.containsBeanDefinition("names")).isFalse();
        verify(mockBeans).register(eq("names"), same(result), eq(ResolvableType.forClassWithGenerics(Supplier.class,
                String.class).getType()));
    }

    @Test
    void postProcessBeanFactory_whenASpyNamesAnAlias_shouldSpyTheBeanItNamesAndRecordTheSpyUnderTheAlias() {
        factory.registerBeanDefinition("auditService", new RootBeanDefinition(AuditService.class));
        factory.registerAlias("auditService", "audit");

        underTest(Map.of(), Map.of("audit", new SpyBean("audit", List.of())), Map.of())
                .postProcessBeanFactory(factory);
        AuditService result = factory.getBean(AuditService.class);

        assertThat(Mockito.mockingDetails(result).isSpy()).isTrue();
        verify(mockBeans).register("audit", result, AuditService.class);
    }

    @Test
    void postProcessBeanFactory_whenAMockNamesASingletonWithoutDefinition_shouldFailSayingItCantBeReplaced() {
        factory.registerSingleton("springApplicationArguments", new Object());
        BeanOverridePostProcessor underTest = underTest(Map.of("springApplicationArguments", new MockBean(
                "springApplicationArguments", ReportStore.class.getName(), false, List.of())), Map.of(), Map.of());

        assertThatThrownBy(() -> underTest.postProcessBeanFactory(factory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cases/ variant 'default': can't mock bean 'springApplicationArguments': it is a singleton"
                        + " registered directly with the bean factory, without a bean definition, so it can't be"
                        + " replaced (referenced by cases/a.yaml)");
    }

    @Test
    void postProcessBeanFactory_whenOnlyTheDefaultsFileNamesAMissingBean_shouldSkipIt() {
        BeanOverridePostProcessor underTest = underTest(
                Map.of("optionalFeature", new MockBean("optionalFeature", null, false, List.of())),
                Map.of("optionalAudit", new SpyBean("optionalAudit", List.of())), Map.of(),
                Set.of("optionalFeature", "optionalAudit"));

        underTest.postProcessBeanFactory(factory);

        assertThat(factory.containsBeanDefinition("optionalFeature")).isFalse();
        assertThat(factory.getBeanPostProcessorCount()).isZero();
    }

    @Test
    void postProcessBeanFactory_whenALaterPostProcessorProxiesASpiedBean_shouldProxyTheSpy() throws Exception {
        factory.registerBeanDefinition("auditService", new RootBeanDefinition(AuditService.class));
        underTest(Map.of(), Map.of("auditService", new SpyBean("auditService", List.of())), Map.of())
                .postProcessBeanFactory(factory);
        BeanNameAutoProxyCreator autoProxyCreator = new BeanNameAutoProxyCreator();
        autoProxyCreator.setBeanNames("auditService");
        autoProxyCreator.setProxyTargetClass(true);
        autoProxyCreator.setBeanFactory(factory);
        factory.addBeanPostProcessor(autoProxyCreator);

        AuditService result = factory.getBean(AuditService.class);

        assertThat(AopUtils.isAopProxy(result)).isTrue();
        Object target = ((Advised) result).getTargetSource().getTarget();
        assertThat(Mockito.mockingDetails(target).isSpy()).isTrue();
        assertThat(result.label("x")).isEqualTo("audit:x");
        verify(mockBeans).register("auditService", target, AuditService.class);
    }

    private BeanOverridePostProcessor underTest(Map<String, MockBean> mocks, Map<String, SpyBean> spies,
                                                Map<String, ValueBean> values) {
        return underTest(mocks, spies, values, Set.of());
    }

    private BeanOverridePostProcessor underTest(Map<String, MockBean> mocks, Map<String, SpyBean> spies,
                                                Map<String, ValueBean> values, Set<String> defaultsOnly) {
        ContextFile context = new ContextFile(LABEL, "com.example.App", List.of(), ContextVariant.EMPTY, Map.of(),
                List.of(), List.of(), 0);
        RootConfiguration root = new RootConfiguration("cases/", context, StubSet.EMPTY, "cases/_defaults.yaml",
                null);
        Map<String, Set<String>> references = Map.of(
                "campainClient", new TreeSet<>(List.of("cases/a.yaml", "cases/b.yaml")),
                "auditService", new TreeSet<>(List.of("cases/a.yaml", "cases/b.yaml")),
                "springApplicationArguments", new TreeSet<>(List.of("cases/a.yaml")));
        VariantPlan plan = new VariantPlan(new VariantKey("cases/", null), root, context, "com.example.App", Map.of(),
                mocks, spies, values, references, defaultsOnly, Set.of());
        return new BeanOverridePostProcessor(plan, mockBeans, mockStubs, mockConverters);
    }

    private static RootBeanDefinition factoryMethod(String method, boolean primary) {
        RootBeanDefinition definition = new RootBeanDefinition();
        definition.setFactoryBeanName("config");
        definition.setFactoryMethodName(method);
        definition.setPrimary(primary);
        return definition;
    }

    public static class Config {

        public Supplier<Map<String, byte[]>> blobSupplier() {
            throw new IllegalStateException("the real bean must not be created");
        }

        public Supplier<String> nameSupplier() {
            return () -> "name";
        }

        @Qualifier("names")
        public Supplier<String> qualifiedSupplier() {
            return () -> "qualified";
        }
    }

    public static class ReportStoreFactory implements FactoryBean<ReportStore> {

        @Override
        public ReportStore getObject() {
            throw new IllegalStateException("the real factory must not be used");
        }

        @Override
        public Class<?> getObjectType() {
            return ReportStore.class;
        }
    }

    public static class GenericFactory<T> implements FactoryBean<T> {

        @Override
        public T getObject() {
            throw new IllegalStateException("the real factory must not be used");
        }

        @Override
        public @Nullable Class<?> getObjectType() {
            return null;
        }
    }

    public record BlobConsumer(Supplier<Map<String, byte[]>> supplier) {
    }

    public interface ReportStore {

        int count();
    }

    public record Sources(List<String> sources) {
    }

    public static class AuditService {

        public String label(String name) {
            return "audit:" + name;
        }
    }
}
