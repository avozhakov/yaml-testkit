package com.yamltestkit.selftest.app.events;

import java.util.List;

/** @param adResponseString the HTTP response body, as the string the client received */
public record ResponseEvent(String requestId, String adResponseString, List<LoggedAd> ads) implements LoggedEvent {
}
