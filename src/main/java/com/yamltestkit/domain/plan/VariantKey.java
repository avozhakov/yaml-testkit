package com.yamltestkit.domain.plan;

import org.jspecify.annotations.Nullable;

/**
 * Identifies a context variant: the glob root whose {@code _context.yaml} declares it and the variant name,
 * {@code null} for the default variant.
 */
public record VariantKey(String root, @Nullable String variant) {

    /** How the variant is named in messages, e.g. {@code cases/ variant 'ebay-off'}. */
    public String describe() {
        return root + " variant '" + (variant == null ? "default" : variant) + "'";
    }
}
