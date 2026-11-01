package com.yamltestkit.infrastructure.spring;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A started application context with its HTTP port and the mocks and spies it holds.
 *
 * @param port the {@code local.server.port} of the context, {@code null} when it runs no web server
 */
public record RunningContext(ConfigurableApplicationContext context, @Nullable Integer port, ContextBeans beans)
        implements AutoCloseable {

    /**
     * @throws IllegalStateException when the application runs no web server
     */
    public int requirePort(String where) {
        if (port == null) {
            throw new IllegalStateException(where + ": the application context has no local.server.port; http"
                    + " calls need a web application");
        }
        return port;
    }

    /**
     * The mock or spy that replaced the named bean. Asks the context for the bean first, so a lazy bean is created
     * (and replaced) before it is stubbed or its calls are read.
     *
     * @param where the YAML location, prefixed to the error
     * @throws IllegalStateException when the bean was not replaced by a mock or spy
     */
    public OverriddenBean overridden(String name, String where) {
        context.getBean(name);
        return beans.find(name).orElseThrow(() -> new IllegalStateException(where + ": bean '" + name
                + "' was not replaced by a mock or spy"));
    }

    @Override
    public void close() {
        context.close();
    }
}
