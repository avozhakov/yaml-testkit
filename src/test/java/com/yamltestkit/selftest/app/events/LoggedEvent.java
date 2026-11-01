package com.yamltestkit.selftest.app.events;

/** An event handed to {@link EventLogger}, as a service hands request / response messages to its Kafka client. */
public sealed interface LoggedEvent permits RequestEvent, ResponseEvent {

    String requestId();
}
