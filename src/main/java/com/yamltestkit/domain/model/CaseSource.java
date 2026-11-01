package com.yamltestkit.domain.model;

import java.net.URI;

/**
 * Where a case file comes from.
 *
 * @param testSourceUri {@code classpath:} URI of the file, which IDEs resolve to the source resource
 * @param root          the non-wildcard prefix of the glob that matched the file, e.g. {@code cases/}
 * @param relativePath  path of the file relative to {@code root}, e.g. {@code discover/ebay-off.yaml}
 */
public record CaseSource(URI testSourceUri, String root, String relativePath) {

    /** How the file is named in failure messages, e.g. {@code cases/discover/ebay-off.yaml}. */
    public String label() {
        return root + relativePath;
    }
}
