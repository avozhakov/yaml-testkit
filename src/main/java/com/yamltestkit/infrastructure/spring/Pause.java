package com.yamltestkit.infrastructure.spring;

/** Waits between cases ({@code settleMs}); a seam so that tests check the wait without sleeping. */
@FunctionalInterface
interface Pause {

    Pause SLEEP = millis -> {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting settleMs", e);
        }
    };

    void millis(int millis);
}
