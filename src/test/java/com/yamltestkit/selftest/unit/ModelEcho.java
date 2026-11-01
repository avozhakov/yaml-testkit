package com.yamltestkit.selftest.unit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Static methods that take converted arguments and return them (or a field of them) for converter cases. */
public final class ModelEcho {

    private ModelEcho() {
    }

    public static AdRequestModel request(AdRequestModel request) {
        return request.toBuilder().maxAds(request.getMaxAds() + 1).build();
    }

    public static String query(QuerySettings settings) {
        return settings.getQuery() + "/" + settings.getMaxAds();
    }

    public static Map<String, String> map(Map<String, String> map) {
        return map;
    }

    public static String orDefault(Optional<String> value) {
        return value.orElse("default");
    }

    public static Optional<String> optional(String value) {
        return Optional.ofNullable(value);
    }

    public static List<Provider> providers(List<Provider> providers) {
        return providers;
    }

    public static boolean isNull(String value) {
        return value == null;
    }
}
