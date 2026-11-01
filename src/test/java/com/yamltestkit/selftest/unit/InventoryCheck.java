package com.yamltestkit.selftest.unit;

import java.util.concurrent.CompletableFuture;

/** Throws for {@code expect.throws}, directly or through a failed future. */
public class InventoryCheck {

    public int available(String sku) {
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku is blank");
        }
        throw new IllegalStateException("db down");
    }

    public CompletableFuture<Integer> availableLater(String sku) {
        return CompletableFuture.failedFuture(new IllegalStateException("db down for " + sku));
    }
}
