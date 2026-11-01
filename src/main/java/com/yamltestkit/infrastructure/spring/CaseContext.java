package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.infrastructure.wiremock.WireMockServices;

/**
 * What an {@code http} / {@code bean} case runs against: its variant's context (or a fresh one, closed with this
 * object) and the WireMock servers of its glob root.
 */
public record CaseContext(RunningContext running, WireMockServices wireMock, boolean fresh) implements AutoCloseable {

    /** Forgets the calls recorded on the context's mocks and spies and the WireMock journals, keeping stubs. */
    public void clearRecordings() {
        running.beans().clearInvocations();
        wireMock.resetRequests();
    }

    @Override
    public void close() {
        if (fresh) {
            running.close();
        }
    }
}
