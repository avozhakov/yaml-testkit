package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.domain.model.ContextFile;
import com.yamltestkit.domain.model.ContextVariant;
import com.yamltestkit.domain.model.RootConfiguration;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.plan.VariantKey;
import com.yamltestkit.domain.plan.VariantPlan;
import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextRegistryTest {

    private static final ContextFile CONTEXT = new ContextFile("cases/_context.yaml", "com.example.App", List.of(),
            ContextVariant.EMPTY, Map.of(), List.of(), List.of(), 0);
    private static final RootConfiguration ROOT = new RootConfiguration("cases/", CONTEXT, StubSet.EMPTY,
            "cases/_caseDefaults.yaml", null);
    private static final VariantPlan PLAN = new VariantPlan(new VariantKey("cases/", null), ROOT, CONTEXT,
            "com.example.App", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), Set.of());

    private ContextStarter mockStarter;
    private RunningContext mockFirst;
    private RunningContext mockSecond;

    private ContextRegistry underTest;

    @BeforeEach
    void setUp() {
        mockStarter = mock(ContextStarter.class);
        mockFirst = mock(RunningContext.class);
        mockSecond = mock(RunningContext.class);
        underTest = new ContextRegistry(mockStarter);
    }

    @Test
    void acquire_whenTheSharedContextIsNotRunning_shouldStartItOnceAndReuseIt() {
        when(mockStarter.start(PLAN, underTest.wireMock(PLAN))).thenReturn(mockFirst);

        ContextRegistry.Acquired first = underTest.acquire(PLAN, false);
        ContextRegistry.Acquired second = underTest.acquire(PLAN, false);

        assertThat(first).isEqualTo(new ContextRegistry.Acquired(mockFirst, true));
        assertThat(second).isEqualTo(new ContextRegistry.Acquired(mockFirst, false));
        assertThat(underTest.running(PLAN.key())).contains(mockFirst);
        verify(mockStarter, times(1)).start(PLAN, underTest.wireMock(PLAN));
    }

    @Test
    void acquire_whenFresh_shouldStartANewContextEachTimeWithoutSharingIt() {
        when(mockStarter.start(PLAN, underTest.wireMock(PLAN))).thenReturn(mockFirst, mockSecond);

        ContextRegistry.Acquired first = underTest.acquire(PLAN, true);
        ContextRegistry.Acquired second = underTest.acquire(PLAN, true);

        assertThat(first.context()).isSameAs(mockFirst);
        assertThat(second.context()).isSameAs(mockSecond);
        assertThat(underTest.running(PLAN.key())).isEmpty();
    }

    @Test
    void acquire_whenTheSharedContextFailsToStart_shouldReportTheFailureForEveryCaseWithoutRetrying() {
        IllegalArgumentException boom = new IllegalArgumentException("boom");
        when(mockStarter.start(PLAN, underTest.wireMock(PLAN))).thenThrow(boom);

        assertThatThrownBy(() -> underTest.acquire(PLAN, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cases/ variant 'default': the application context failed to start: boom")
                .hasCause(boom);
        assertThatThrownBy(() -> underTest.acquire(PLAN, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("cases/ variant 'default': the application context failed to start: boom")
                .hasCause(boom);
        verify(mockStarter, times(1)).start(PLAN, underTest.wireMock(PLAN));
    }

    @Test
    void wireMock_whenCalledAgainForTheSameRoot_shouldReuseTheServers() {
        WireMockServices result = underTest.wireMock(PLAN);

        assertThat(underTest.wireMock(PLAN)).isSameAs(result);
    }

    @Test
    void close_whenSharedContextsRun_shouldCloseThemAll() {
        when(mockStarter.start(PLAN, underTest.wireMock(PLAN))).thenReturn(mockFirst);
        underTest.acquire(PLAN, false);

        underTest.close();

        verify(mockFirst).close();
        assertThat(underTest.running(PLAN.key())).isEmpty();
    }
}
