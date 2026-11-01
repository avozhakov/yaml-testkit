package com.yamltestkit.selftest.unit;

/**
 * A hand-written {@code builder().setX()} class, like generated clients' models. The builder style is the point of
 * this fixture, so it is written by hand rather than with Lombok.
 */
public final class QuerySettings {

    private final int maxAds;
    private final String query;

    private QuerySettings(Builder builder) {
        this.maxAds = builder.maxAds;
        this.query = builder.query;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getMaxAds() {
        return maxAds;
    }

    public String getQuery() {
        return query;
    }

    public static final class Builder {

        private int maxAds;
        private String query;

        public Builder setMaxAds(int maxAds) {
            this.maxAds = maxAds;
            return this;
        }

        public Builder setQuery(String query) {
            this.query = query;
            return this;
        }

        public QuerySettings build() {
            return new QuerySettings(this);
        }
    }
}
