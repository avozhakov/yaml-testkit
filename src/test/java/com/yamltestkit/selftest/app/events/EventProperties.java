package com.yamltestkit.selftest.app.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** @param responseDelay how long after the response the response event is sent */
@ConfigurationProperties("fixture.events")
public record EventProperties(Duration responseDelay) {
}
