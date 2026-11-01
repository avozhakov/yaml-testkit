package com.yamltestkit.selftest.app;

import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Owns the periodic feed reload (imitates {@code CacheReloaderScheduler}): the cases trigger a reload by calling this
 * bean, while {@code fixture.feed.reload-interval} in {@code _context.yaml} keeps the schedule from firing.
 */
@Component
@RequiredArgsConstructor
public class FeedReloadScheduler {

    private final FeedCache feedCache;
    private final FeedProperties properties;
    private final AsyncTaskExecutor executor;

    @Scheduled(initialDelayString = "${fixture.feed.reload-interval}", fixedDelayString = "${fixture.feed.reload-interval}")
    public void reloadFeeds() {
        feedCache.reload();
    }

    /** Reloads on another thread after {@code fixture.feed.async-reload-delay}, so a case has to wait for the state. */
    public void reloadFeedsLater() {
        CompletableFuture.runAsync(feedCache::reload, CompletableFuture.delayedExecutor(
                properties.asyncReloadDelay().toMillis(), TimeUnit.MILLISECONDS, executor));
    }
}
