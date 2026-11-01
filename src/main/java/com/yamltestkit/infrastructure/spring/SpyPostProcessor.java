package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.domain.model.SpyBean;
import com.yamltestkit.infrastructure.mockito.StubApplier;
import lombok.RequiredArgsConstructor;
import org.mockito.Mockito;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Wraps the named beans in Mockito spies once they are initialised, with their boot-time stubs applied in the
 * {@code doAnswer} form (so real methods aren't invoked while stubbing). Added ahead of the context's own bean
 * post-processors by {@link BeanOverridePostProcessor}, so it sees the target rather than an AOP proxy; a bean that
 * still arrives as a proxy (e.g. made by a {@code FactoryBean}) can't be spied and fails the startup. The spies are
 * keyed by the name of their bean definition (an alias already resolved) and recorded under their YAML name.
 */
@RequiredArgsConstructor
final class SpyPostProcessor implements BeanPostProcessor {

    private final Map<String, SpyBean> spies;
    private final ContextBeans beans;
    private final StubApplier stubs;
    private final String wherePrefix;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        SpyBean spyBean = spies.get(beanName);
        if (spyBean == null || bean instanceof FactoryBean<?>) {
            return bean;
        }
        if (AopUtils.isAopProxy(bean) || Proxy.isProxyClass(bean.getClass())) {
            throw new IllegalStateException(wherePrefix + spyBean.name() + ": bean '" + beanName + "' is a proxy ("
                    + bean.getClass().getName() + ") and can't be spied; spy the bean it delegates to instead");
        }
        Class<?> type = bean.getClass();
        Object spy = Mockito.mock(type, Mockito.withSettings().spiedInstance(bean).name(beanName)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        stubs.apply(spy, type, spyBean.stubs(), beans.startupErrors(), wherePrefix + spyBean.name());
        beans.register(spyBean.name(), spy, type);
        return spy;
    }
}
