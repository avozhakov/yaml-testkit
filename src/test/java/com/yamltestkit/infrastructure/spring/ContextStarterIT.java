package com.yamltestkit.infrastructure.spring;

import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.ContextVariant;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.MockBean;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.plan.VariantKey;
import com.yamltestkit.domain.plan.VariantPlan;
import com.yamltestkit.infrastructure.logback.LogCapture;
import com.yamltestkit.infrastructure.mockito.StubApplier;
import com.yamltestkit.infrastructure.mockito.StubErrors;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Starts a tiny non-web application through {@link ContextStarter}: the variant's properties win, the bean override
 * post-processor and the log capture are installed, and a boot-time stub failure closes the started context before
 * the startup fails.
 */
class ContextStarterIT {

    private static final String LABEL = "cases/_context.yaml";
    private static final String WHERE = LABEL + ", mockBeans greeting";
    private static final Stub GREETING_STUB = new Stub(new MethodRef("text", null), null,
            new StubAnswer.Returns(TextNode.valueOf("hi")));
    private static final Map<String, String> PROPERTIES = Map.of(
            "spring.main.web-application-type", "none",
            "spring.config.name", "context-starter-it",
            "starter.mode", "${wiremock.partner.host}");
    private static final Set<String> LOGGERS = Set.of("com.example.FeedLoader");

    private StubApplier mockStubs;
    private ValueConverters mockConverters;
    private LogCapture mockLogs;
    private WireMockServices mockWireMock;

    private ContextStarter underTest;

    @BeforeEach
    void setUp() {
        mockStubs = mock(StubApplier.class);
        mockConverters = mock(ValueConverters.class);
        mockLogs = mock(LogCapture.class);
        mockWireMock = mock(WireMockServices.class);
        PROPERTIES.forEach((key, value) -> when(mockWireMock.resolve(value, LABEL + ", property " + key))
                .thenReturn(value.replace("${wiremock.partner.host}", "localhost")));
        ClosingProbe.CLOSED.set(false);
        underTest = new ContextStarter(mockStubs, mockConverters, mockLogs);
    }

    @Test
    void start_whenTheApplicationStarts_shouldReturnTheContextWithItsMocksAndTheVariantsProperties() {
        try (RunningContext result = underTest.start(plan(), mockWireMock)) {
            Object greeting = result.context().getBean("greeting");

            assertThat(Mockito.mockingDetails(greeting).isMock()).isTrue();
            assertThat(result.beans().find("greeting")).contains(new OverriddenBean(greeting, Greeting.class));
            assertThat(result.port()).isNull();
            assertThat(result.context().getEnvironment().getProperty("starter.mode")).isEqualTo("localhost");
            assertThat(result.context().getEnvironment().getProperty("server.port")).isEqualTo("0");
            verify(mockStubs).apply(greeting, Greeting.class, List.of(GREETING_STUB), result.beans().startupErrors(),
                    WHERE);
            verify(mockLogs).attach(LOGGERS);
        }
    }

    @Test
    void start_whenABootTimeStubFailsDuringStartup_shouldCloseTheContextThenFail() {
        ArgumentCaptor<Object> greeting = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<StubErrors> startupErrors = ArgumentCaptor.forClass(StubErrors.class);
        IllegalStateException cause = new IllegalStateException("can't convert the argument");
        doAnswer(invocation -> {
            invocation.<StubErrors>getArgument(3).record(WHERE + ": boom", cause);
            return null;
        }).when(mockStubs).apply(greeting.capture(), eq(Greeting.class), eq(List.of(GREETING_STUB)),
                startupErrors.capture(), eq(WHERE));

        assertThatThrownBy(() -> underTest.start(plan(), mockWireMock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cases/ variant 'default': a boot-time stub failed while the context started: " + WHERE
                        + ": boom")
                .cause()
                .hasCause(cause);

        assertThat(ClosingProbe.CLOSED).isTrue();
        assertThat(Mockito.mockingDetails(greeting.getValue()).isMock()).isTrue();
        assertThat(startupErrors.getValue().drain()).isEmpty();
    }

    private static VariantPlan plan() {
        ContextFile context = new ContextFile(LABEL, StarterApplication.class.getName(), List.of(),
                ContextVariant.EMPTY, Map.of(), List.of("partner"), List.of(), 0);
        RootConfiguration root = new RootConfiguration("cases/", context, StubSet.EMPTY, "cases/_caseDefaults.yaml",
                null);
        return new VariantPlan(new VariantKey("cases/", null), root, context, StarterApplication.class.getName(),
                PROPERTIES, Map.of("greeting", new MockBean("greeting", null, false, List.of(GREETING_STUB))),
                Map.of(), Map.of(), Map.of("greeting", Set.of(LABEL)), Set.of(), LOGGERS);
    }

    public interface Greeting {

        String text();
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    public static class StarterApplication {

        @Bean
        public Greeting greeting() {
            return () -> "real";
        }

        @Bean
        public ClosingProbe closingProbe() {
            return new ClosingProbe();
        }
    }

    /** Records that the context was closed; static because the context is out of the test's reach after failing. */
    public static class ClosingProbe implements DisposableBean {

        static final AtomicBoolean CLOSED = new AtomicBoolean();

        @Override
        public void destroy() {
            CLOSED.set(true);
        }
    }
}
