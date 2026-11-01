package com.yamltestkit.selftest.unit;

import java.util.Locale;

/** A static utility: calling {@link #slug} must not create an instance. */
public final class Slugs {

    public Slugs() {
        throw new IllegalStateException("a static method call must not create an instance");
    }

    public static String slug(String title) {
        return title.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }
}
