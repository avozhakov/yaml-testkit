package com.yamltestkit.selftest.app.ads;

import com.yamltestkit.selftest.app.events.EventLogger;
import com.yamltestkit.selftest.app.events.EventProperties;
import com.yamltestkit.selftest.app.events.LoggedAd;
import com.yamltestkit.selftest.app.events.RequestEvent;
import com.yamltestkit.selftest.app.events.ResponseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Hands the request and response events to {@link EventLogger} from the executor (imitates the data-logging
 * scheduler): the request event at once, the response event {@code fixture.events.response-delay} after the response,
 * so a case has to wait for it.
 */
@Component
@RequiredArgsConstructor
public class AdEventPublisher {

    static final String REQUEST_TOPIC = "ad_request";
    static final String RESPONSE_TOPIC = "ad_response";

    private final EventLogger eventLogger;
    private final AsyncTaskExecutor executor;
    private final EventProperties properties;

    public void publish(AdContext context, AdSelection selection, String responseBody) {
        String requestId = context.requestId();
        RequestEvent request = new RequestEvent(requestId, context.query().placementId(), context.terms());
        ResponseEvent response = new ResponseEvent(requestId, responseBody, selection.ads().stream()
                .map(ad -> new LoggedAd(ad.adSelectionId(), ad.ad().qt()))
                .toList());
        executor.execute(() -> eventLogger.send(requestId, request, REQUEST_TOPIC));
        CompletableFuture.runAsync(() -> eventLogger.send(requestId, response, RESPONSE_TOPIC),
                CompletableFuture.delayedExecutor(properties.responseDelay().toMillis(), TimeUnit.MILLISECONDS,
                        executor));
    }
}
