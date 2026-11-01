package com.yamltestkit.domain.model;

import java.util.List;

/**
 * A WireMock stub on a logical service.
 *
 * @param responses one response, or the responses of a {@code sequence} (the last one repeats)
 */
public record HttpStub(String service, RequestMatcher request, List<StubResponse> responses, boolean sequence) {

    public HttpStub {
        responses = List.copyOf(responses);
    }
}
