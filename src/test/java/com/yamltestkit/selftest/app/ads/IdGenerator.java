package com.yamltestkit.selftest.app.ads;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Request and ad selection ids generated at runtime, so the cases can only correlate them, never hard-code them. */
@Component
public class IdGenerator {

    private final AtomicLong next = new AtomicLong(1000);

    public String nextRequestId() {
        return "req-" + next.incrementAndGet();
    }

    public long nextAdSelectionId() {
        return next.incrementAndGet();
    }
}
