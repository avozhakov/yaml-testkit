package com.yamltestkit.domain.model;

import java.util.List;

/**
 * One layer of stubs: {@code mocks}, {@code spies} and WireMock {@code http} stubs.
 */
public record StubSet(List<BeanEntry> mocks, List<BeanEntry> spies, List<HttpStub> http) {

    public static final StubSet EMPTY = new StubSet(List.of(), List.of(), List.of());

    public StubSet {
        mocks = List.copyOf(mocks);
        spies = List.copyOf(spies);
        http = List.copyOf(http);
    }

    public boolean isEmpty() {
        return mocks.isEmpty() && spies.isEmpty() && http.isEmpty();
    }
}
