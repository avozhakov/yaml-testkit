package com.yamltestkit.selftest.unit;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A response-like object for {@code assert} operator cases: {@code gtin} is written as {@code null}, while
 * {@link Ad} omits its {@code null} fields, like generated models with {@code @JsonInclude(NON_NULL)}.
 */
public record AdResponse(String requestId, List<Ad> ads, List<DiagEntry> diag, @Nullable String gtin) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ad(String title, String qt, String section, int price, @Nullable String asin) {
    }

    /** A diagnostics entry whose payload is JSON inside a string. */
    public record DiagEntry(String step, String payload) {
    }
}
