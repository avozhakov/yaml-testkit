package com.yamltestkit.selftest.app.tracking;

import java.util.List;

/** Body of the {@code POST /urls} response: one click URL per item, matched by item id. */
public record TrackingResponse(List<TrackingUrl> urls) {
}
