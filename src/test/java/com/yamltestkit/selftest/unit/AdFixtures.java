package com.yamltestkit.selftest.unit;

import java.util.List;

public final class AdFixtures {

    private AdFixtures() {
    }

    public static AdResponse response(String requestId) {
        return new AdResponse(requestId,
                List.of(new AdResponse.Ad("Red shoes", "shoes", "top", 120, null),
                        new AdResponse.Ad("Blue shoes", "shoes", "side", 80, null),
                        new AdResponse.Ad("Socks", "socks", "top", 15, "B00SOCKS")),
                List.of(new AdResponse.DiagEntry("dedup", "{\"dropped\": [\"ad-9\"]}"),
                        new AdResponse.DiagEntry("rank", "{\"dropped\": []}")),
                null);
    }
}
