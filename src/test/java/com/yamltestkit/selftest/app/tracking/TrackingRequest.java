package com.yamltestkit.selftest.app.tracking;

import java.util.List;

/** Body of the {@code POST /urls} request. */
public record TrackingRequest(List<TrackingItem> items) {
}
