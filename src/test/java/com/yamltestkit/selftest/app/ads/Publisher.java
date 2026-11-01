package com.yamltestkit.selftest.app.ads;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/** Omits a {@code null} {@code competitivenessId}, like generated client-library models. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Publisher(long placementId, @Nullable String competitivenessId) {
}
