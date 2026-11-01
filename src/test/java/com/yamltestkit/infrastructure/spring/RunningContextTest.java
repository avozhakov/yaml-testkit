package com.yamltestkit.infrastructure.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunningContextTest {

    private static final String WHERE = "cases/a.yaml, case 'c', mocks feedRepository";
    private static final OverriddenBean FEED_REPOSITORY = new OverriddenBean(new Object(), Object.class);

    private ConfigurableApplicationContext mockContext;
    private ContextBeans mockBeans;

    private RunningContext underTest;

    @BeforeEach
    void setUp() {
        mockContext = mock(ConfigurableApplicationContext.class);
        mockBeans = mock(ContextBeans.class);
        underTest = new RunningContext(mockContext, null, mockBeans);
    }

    @Test
    void overridden_whenTheBeanWasReplaced_shouldCreateItFirstAndReturnTheMock() {
        when(mockBeans.find("feedRepository")).thenReturn(Optional.of(FEED_REPOSITORY));

        OverriddenBean result = underTest.overridden("feedRepository", WHERE);

        assertThat(result).isSameAs(FEED_REPOSITORY);
        InOrder order = inOrder(mockContext, mockBeans);
        order.verify(mockContext).getBean("feedRepository");
        order.verify(mockBeans).find("feedRepository");
    }

    @Test
    void overridden_whenTheBeanWasNotReplaced_shouldFailNamingTheBean() {
        when(mockBeans.find("feedCache")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> underTest.overridden("feedCache", WHERE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(WHERE + ": bean 'feedCache' was not replaced by a mock or spy");
    }

    @Test
    void requirePort_whenTheContextRunsNoWebServer_shouldFail() {
        assertThatThrownBy(() -> underTest.requirePort(WHERE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith(WHERE + ": the application context has no local.server.port");
    }
}
