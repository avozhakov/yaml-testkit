package com.yamltestkit.selftest.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeService {

    private final ExchangeSources exchangeSources;

    public int count() {
        return exchangeSources.sources().size();
    }
}
