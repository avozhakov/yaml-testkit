package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.SpyBean;
import com.yamltestkit.domain.model.ValueBean;
import com.yamltestkit.domain.plan.VariantPlan;
import com.yamltestkit.infrastructure.mockito.StubApplier;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces the beans of a {@link VariantPlan} by name before the context instantiates any bean. Works in
 * {@code postProcessBeanFactory} (like Spring's own bean-override post-processor), when the scanned and {@code @Bean}
 * definitions exist; a post-processor added by an initializer would run {@code postProcessBeanDefinitionRegistry}
 * before {@code ConfigurationClassPostProcessor} registered them.
 * <ul>
 *     <li>mock: the definition is replaced by one whose instance supplier returns a Mockito mock with the boot-time
 *     stubs applied; its target type keeps the original definition's generics (so it still autowires), the product
 *     type for a {@code FactoryBean} definition. Without a definition, the entry's {@code type} creates the bean.</li>
 *     <li>spy: a {@link SpyPostProcessor} is added from here, so it runs ahead of the context's own bean
 *     post-processors (the AOP auto-proxy creator included) and wraps the target, not a proxy.</li>
 *     <li>value bean: the definition is replaced by (or created as) one supplying the converted value.</li>
 * </ul>
 * A replaced definition keeps the original's qualifiers, {@code fallback} and lazy-init flags, and is primary when
 * the original or the entry is. An alias resolves to the bean it names. A mocked or spied name with no definition
 * (and, for a mock, no {@code type}) fails the startup, naming every file that references the bean, unless only
 * {@code _defaults.yaml} names it: then the variant simply has no such bean. A name matching a singleton registered
 * without a definition fails, since such a bean can't be replaced.
 */
@RequiredArgsConstructor
final class BeanOverridePostProcessor implements BeanFactoryPostProcessor {

    private final VariantPlan plan;
    private final ContextBeans beans;
    private final StubApplier stubs;
    private final ValueConverters converters;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;
        plan.mocks().values().forEach(mock -> replaceWithMock(factory, registry, mock));
        plan.values().values().forEach(value -> replaceWithValue(factory, registry, value));
        Map<String, SpyBean> spies = spiesByDefinition(factory, registry);
        if (!spies.isEmpty()) {
            factory.addBeanPostProcessor(new SpyPostProcessor(spies, beans, stubs,
                    plan.context().label() + ", spyBeans "));
        }
    }

    private void replaceWithMock(ConfigurableListableBeanFactory factory, BeanDefinitionRegistry registry,
                                 MockBean mock) {
        String name = mock.name();
        String where = plan.context().label() + ", mockBeans " + name;
        @Nullable String definitionName = definitionName(factory, registry, name, "mock");
        ResolvableType type;
        RootBeanDefinition definition;
        if (definitionName != null) {
            type = declaredType(factory, definitionName, mock, where);
            definition = replacement(factory, registry, definitionName, type.toClass(), mock.primary());
        } else if (mock.type() != null) {
            type = ResolvableType.forClass(TypeNames.load(mock.type(), where + ": type"));
            definition = new RootBeanDefinition(type.toClass());
            definition.setPrimary(mock.primary());
        } else if (plan.defaultsOnly().contains(name)) {
            return;
        } else {
            throw unknownBean(name, "mock");
        }
        Class<?> rawType = type.toClass();
        definition.setTargetType(type);
        definition.setScope(BeanDefinition.SCOPE_SINGLETON);
        definition.setInstanceSupplier(() -> {
            Object instance = Mockito.mock(rawType, Mockito.withSettings().name(name));
            stubs.apply(instance, type.getType(), mock.stubs(), beans.startupErrors(), where);
            beans.register(name, instance, type.getType());
            return instance;
        });
        registry.registerBeanDefinition(definitionName != null ? definitionName : name, definition);
    }

    private void replaceWithValue(ConfigurableListableBeanFactory factory, BeanDefinitionRegistry registry,
                                  ValueBean value) {
        String where = plan.context().label() + ", valueBeans " + value.name();
        Class<?> type = TypeNames.load(value.type(), where + ": type");
        Object instance = converters.fromYaml(value.value(), type, where + ".value");
        @Nullable String definitionName = definitionName(factory, registry, value.name(), "replace");
        RootBeanDefinition definition;
        if (definitionName != null) {
            definition = replacement(factory, registry, definitionName, type, value.primary());
        } else {
            definition = new RootBeanDefinition(type);
            definition.setPrimary(value.primary());
        }
        definition.setInstanceSupplier(() -> instance);
        registry.registerBeanDefinition(definitionName != null ? definitionName : value.name(), definition);
    }

    /** Spied beans by the name of their definition; a bean only {@code _defaults.yaml} names may be missing. */
    private Map<String, SpyBean> spiesByDefinition(ConfigurableListableBeanFactory factory,
                                                   BeanDefinitionRegistry registry) {
        Map<String, SpyBean> spies = new HashMap<>();
        for (SpyBean spy : plan.spies().values()) {
            @Nullable String definitionName = definitionName(factory, registry, spy.name(), "spy");
            if (definitionName != null) {
                spies.put(definitionName, spy);
            } else if (!plan.defaultsOnly().contains(spy.name())) {
                throw unknownBean(spy.name(), "spy");
            }
        }
        return spies;
    }

    /**
     * The name of the definition a YAML name refers to: the name itself, or the bean an alias points to.
     *
     * @return {@code null} when the context has no such definition
     * @throws IllegalStateException when the name matches a singleton registered without a definition
     */
    private @Nullable String definitionName(ConfigurableListableBeanFactory factory, BeanDefinitionRegistry registry,
                                            String name, String action) {
        String canonical = registry.isAlias(name) ? aliasTarget(registry, name) : name;
        if (registry.containsBeanDefinition(canonical)) {
            return canonical;
        }
        if (factory.containsSingleton(canonical)) {
            throw new IllegalStateException(plan.key().describe() + ": can't " + action + " bean '" + name + "': it"
                    + " is a singleton registered directly with the bean factory, without a bean definition, so it"
                    + " can't be replaced (referenced by " + String.join(", ", plan.referencesOf(name)) + ")");
        }
        return null;
    }

    private static String aliasTarget(BeanDefinitionRegistry registry, String alias) {
        return Arrays.stream(registry.getBeanDefinitionNames())
                .filter(candidate -> Arrays.asList(registry.getAliases(candidate)).contains(alias))
                .findFirst()
                .orElse(alias);
    }

    /**
     * The type the mock stands for, with generics, without instantiating anything: asking for the type first makes
     * Spring introspect a {@code @Bean} method, whose generic return type the merged definition then reports. For a
     * {@code FactoryBean} definition it is the product type (its generic, else Spring's prediction without
     * initialising the factory), else the entry's {@code type}.
     */
    private ResolvableType declaredType(ConfigurableListableBeanFactory factory, String name, MockBean mock,
                                        String where) {
        @Nullable Class<?> rawType = factory.getType(name, false);
        ResolvableType declared = factory.getMergedBeanDefinition(name).getResolvableType();
        if (factory.isFactoryBean(name)) {
            ResolvableType product = declared.as(FactoryBean.class).getGeneric();
            if (product.resolve() != null) {
                return product;
            }
            if (rawType != null) {
                return ResolvableType.forClass(rawType);
            }
            if (mock.type() != null) {
                return ResolvableType.forClass(TypeNames.load(mock.type(), where + ": type"));
            }
            throw new IllegalStateException(where + ": bean '" + name + "' is made by a FactoryBean whose product"
                    + " type can't be determined without creating it; add `type` to its mockBeans entry");
        }
        if (declared.resolve() != null) {
            return declared;
        }
        if (rawType == null) {
            throw new IllegalStateException(where + ": the type of bean '" + name + "' can't be determined without"
                    + " creating it; add `type` to its mockBeans entry");
        }
        return ResolvableType.forClass(rawType);
    }

    /**
     * A definition of {@code type} replacing the named one, which is removed: it keeps the original's qualifiers
     * (those of the definition and, for a {@code @Bean} method, the method's qualifier annotations), {@code fallback}
     * and lazy-init flags, and is primary when the original or the entry is.
     */
    private static RootBeanDefinition replacement(ConfigurableListableBeanFactory factory,
                                                  BeanDefinitionRegistry registry, String name, Class<?> type,
                                                  boolean primary) {
        BeanDefinition original = registry.getBeanDefinition(name);
        RootBeanDefinition definition = new RootBeanDefinition(type);
        definition.setPrimary(primary || original.isPrimary());
        definition.setFallback(original.isFallback());
        definition.setLazyInit(original.isLazyInit());
        if (original instanceof AbstractBeanDefinition abstractOriginal) {
            definition.copyQualifiersFrom(abstractOriginal);
        }
        factory.getType(name, false); // resolves a @Bean method, which the merged definition then reports
        if (factory.getMergedBeanDefinition(name) instanceof RootBeanDefinition merged) {
            @Nullable Method factoryMethod = merged.getResolvedFactoryMethod();
            if (factoryMethod != null) {
                definition.setQualifiedElement(factoryMethod);
            }
        }
        registry.removeBeanDefinition(name);
        return definition;
    }

    private IllegalStateException unknownBean(String name, String kind) {
        return new IllegalStateException(plan.key().describe() + ": can't " + kind + " bean '" + name
                + "': the context has no bean of that name (referenced by " + String.join(", ", plan.referencesOf(name))
                + ")" + ("mock".equals(kind) ? "; to create it, add `type` to its mockBeans entry in "
                + plan.context().label() : ""));
    }
}
