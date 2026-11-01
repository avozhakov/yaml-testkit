package com.yamltestkit.selftest.app.discover;

import com.yamltestkit.selftest.app.PartnerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Answers a Discover request with the partner's search results (imitates the {@code /di} flow). */
@Service
@RequiredArgsConstructor
public class DiscoverService {

    private static final String SUPPORTED_VERSION = "3.0";

    private final PartnerClient partnerClient;

    public void checkVersion(String version) {
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new UnsupportedVersionException("unsupported version " + version);
        }
    }

    public DiscoverResponse discover(String version, DiscoverRequest request) {
        return new DiscoverResponse(version, request.meta().requestId(),
                partnerClient.search(request.intent().prompt(), request.intent().adCount()).stream()
                        .map(item -> new DiscoverResponse.Item(item.id(), item.title()))
                        .toList());
    }
}
