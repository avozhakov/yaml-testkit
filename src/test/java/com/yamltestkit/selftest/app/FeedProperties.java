package com.yamltestkit.selftest.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code fixture.feed.reload-interval}, the schedule's period, is read by {@link FeedReloadScheduler}'s
 * {@code @Scheduled} placeholder.
 *
 * @param asyncReloadDelay how long {@link FeedReloadScheduler#reloadFeedsLater()} waits before reloading
 */
@ConfigurationProperties("fixture.feed")
public record FeedProperties(Duration asyncReloadDelay) {
}
