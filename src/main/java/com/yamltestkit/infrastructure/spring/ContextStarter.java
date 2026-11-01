package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.plan.VariantPlan;
import com.yamltestkit.infrastructure.logback.LogCapture;
import com.yamltestkit.infrastructure.mockito.StubApplier;
import com.yamltestkit.infrastructure.reflect.TypeNames;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import lombok.RequiredArgsConstructor;
import org.opentest4j.AssertionFailedError;
import org.springframework.boot.Banner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Starts the application of a {@link VariantPlan}. The variant's properties ({@code ${wiremock.*}} resolved,
 * {@code server.port=0} unless set) go into a property source added first to a new environment handed to
 * {@link SpringApplicationBuilder}, as Spring Boot's own test context loader does: they win over the application's
 * configuration files and are visible to what Boot reads before the context exists ({@code spring.config.name},
 * profiles, {@code logging.level.*}), which neither default properties nor an initializer would achieve. An
 * initializer adds the {@link BeanOverridePostProcessor} and (re)attaches log capture to the variant's loggers: it
 * runs after Boot has (re)initialized logging, which may have dropped the appender, and before any bean is created,
 * so a fresh context's startup events are captured.
 */
@RequiredArgsConstructor
class ContextStarter {

    static final String PROPERTY_SOURCE = "yamlTestkit";
    static final String PORT_PROPERTY = "local.server.port";

    private final StubApplier stubs;
    private final ValueConverters converters;
    private final LogCapture logs;

    /**
     * @throws RuntimeException whatever the application's startup throws, or an {@link IllegalStateException} when a
     *                          boot-time stub failed during startup
     */
    RunningContext start(VariantPlan plan, WireMockServices wireMock) {
        String where = plan.key().describe();
        Class<?> application = TypeNames.load(plan.application(), plan.context().label() + ": application");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE,
                properties(plan, wireMock)));
        ContextBeans beans = new ContextBeans();
        ConfigurableApplicationContext context = new SpringApplicationBuilder(application)
                .environment(environment)
                .profiles(plan.context().profiles().toArray(String[]::new))
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .registerShutdownHook(false)
                .initializers(started -> {
                    logs.attach(plan.loggers());
                    started.addBeanFactoryPostProcessor(new BeanOverridePostProcessor(plan, beans, stubs, converters));
                })
                .run();
        List<AssertionFailedError> stubFailures = beans.startupErrors().drain();
        if (!stubFailures.isEmpty()) {
            context.close();
            throw new IllegalStateException(where + ": a boot-time stub failed while the context started: "
                    + stubFailures.getFirst().getMessage(), stubFailures.getFirst());
        }
        return new RunningContext(context, context.getEnvironment().getProperty(PORT_PROPERTY, Integer.class), beans);
    }

    private static Map<String, Object> properties(VariantPlan plan, WireMockServices wireMock) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", "0");
        plan.properties().forEach((key, value) -> properties.put(key,
                wireMock.resolve(value, plan.context().label() + ", property " + key)));
        return properties;
    }
}
