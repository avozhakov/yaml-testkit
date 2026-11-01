package com.yamltestkit.selftest.app.ads;

/**
 * An ad request as received.
 *
 * @param queryTerm the {@code qt} parameter, decoded once by the servlet container
 * @param privacy   {@code true} on the privacy endpoint, which keeps {@code advertiserUrl} on each ad
 */
public record AdQuery(String endpoint, long placementId, String queryTerm, String version, String provider,
                      boolean debug, boolean privacy) {
}
