package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.infrastructure.wiremock.WireMockServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CaseContextTest {

    private ConfigurableApplicationContext mockContext;
    private ContextBeans mockBeans;
    private WireMockServices mockWireMock;
    private RunningContext running;

    @BeforeEach
    void setUp() {
        mockContext = mock(ConfigurableApplicationContext.class);
        mockBeans = mock(ContextBeans.class);
        mockWireMock = mock(WireMockServices.class);
        running = new RunningContext(mockContext, null, mockBeans);
    }

    @Test
    void clearRecordings_whenCalled_shouldForgetRecordedCallsAndRequestsKeepingStubs() {
        CaseContext underTest = new CaseContext(running, mockWireMock, false);

        underTest.clearRecordings();

        verify(mockBeans).clearInvocations();
        verify(mockWireMock).resetRequests();
        verify(mockBeans, never()).resetAll();
        verify(mockWireMock, never()).resetAll();
    }

    @Test
    void close_whenTheContextIsFresh_shouldCloseIt() {
        CaseContext underTest = new CaseContext(running, mockWireMock, true);

        underTest.close();

        verify(mockContext).close();
    }

    @Test
    void close_whenTheContextIsShared_shouldKeepItOpen() {
        CaseContext underTest = new CaseContext(running, mockWireMock, false);

        underTest.close();

        verify(mockContext, never()).close();
    }
}
