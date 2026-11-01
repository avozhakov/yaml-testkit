package com.yamltestkit.selftest.app;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Fixture application of the engine's self-tests (design D15), isolated from other applications on the classpath:
 * only the web auto-configurations it needs (not {@code @EnableAutoConfiguration}, which would pull Redis, Kafka and
 * JPA from the test classpath) and a component scan of its own package. Its cases' {@code _context.yaml} sets
 * {@code spring.config.name: selftest}, so no other {@code application*.yml} is loaded. Scheduling is on so the feed
 * reload schedule exists, and is silenced by property in the cases.
 */
@SpringBootConfiguration
@ImportAutoConfiguration({
        TomcatServletWebServerAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        ErrorMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        TaskExecutionAutoConfiguration.class
})
@ComponentScan
@ConfigurationPropertiesScan
@EnableScheduling
public class SelftestApplication {
}
