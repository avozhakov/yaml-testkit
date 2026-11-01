package com.yamltestkit.selftest.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Exposes the {@link FixtureProperties} to the cases. */
@Component
@RequiredArgsConstructor
public class ModeService {

    private final FixtureProperties properties;

    public String mode() {
        return properties.mode();
    }

    public String source() {
        return properties.source();
    }
}
