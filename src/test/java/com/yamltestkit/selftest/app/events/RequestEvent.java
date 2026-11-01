package com.yamltestkit.selftest.app.events;

import java.util.List;

public record RequestEvent(String requestId, long placementId, List<String> queryTerms) implements LoggedEvent {
}
