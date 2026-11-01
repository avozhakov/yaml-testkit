package com.yamltestkit.selftest.app.discover;

import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * {@code POST /discover} (imitates {@code /di}): the version header is checked at once, the partner is called on the
 * executor, so the response completes asynchronously.
 */
@RestController
@RequiredArgsConstructor
public class DiscoverController {

    static final String VERSION_HEADER = "x-discover-version";

    private final DiscoverService discoverService;
    private final AsyncTaskExecutor executor;

    @PostMapping(path = "/discover", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<DiscoverResponse> discover(@RequestHeader(VERSION_HEADER) String version,
                                                        @RequestBody DiscoverRequest request) {
        discoverService.checkVersion(version);
        return CompletableFuture.supplyAsync(() -> discoverService.discover(version, request), executor);
    }
}
