package com.yamltestkit.selftest.app.events;

import org.jspecify.annotations.Nullable;

/** An ad of the response event; {@code qt} is the DSP's term, logged whatever the response renders. */
public record LoggedAd(long adSelectionId, @Nullable String qt) {
}
