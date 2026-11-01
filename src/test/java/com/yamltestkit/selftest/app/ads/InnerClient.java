package com.yamltestkit.selftest.app.ads;

import org.springframework.stereotype.Component;

import java.util.List;

/** Imitates the intent-identifier client inside {@code IntentFilter}: names the terms to block. The cases mock it. */
@Component
public class InnerClient {

    public List<String> blockedTerms(List<String> terms) {
        throw new IllegalStateException("no intent service in tests");
    }
}
