package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.infrastructure.mockito.StubErrors;
import org.mockito.Mockito;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mocks and spies of one running context by bean name, recorded as the context creates them, for stubbing,
 * verification and the per-case reset. Thread-safe: a lazy bean may be created from any thread.
 */
public final class ContextBeans {

    private final Map<String, OverriddenBean> beans = new ConcurrentHashMap<>();
    private final StubErrors startupErrors = new StubErrors();

    void register(String name, Object instance, Type declaredType) {
        beans.put(name, new OverriddenBean(instance, declaredType));
    }

    public Optional<OverriddenBean> find(String name) {
        return Optional.ofNullable(beans.get(name));
    }

    /** Errors raised by boot-time stubs while the context starts; reported as a startup failure. */
    public StubErrors startupErrors() {
        return startupErrors;
    }

    /** Forgets every stub and recorded call; a spy stays a spy, calling real methods again. */
    public void resetAll() {
        beans.values().forEach(bean -> Mockito.reset(bean.instance()));
    }

    /** Forgets recorded calls, keeping stubs. */
    public void clearInvocations() {
        beans.values().forEach(bean -> Mockito.clearInvocations(bean.instance()));
    }
}
