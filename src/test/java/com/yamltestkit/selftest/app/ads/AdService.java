package com.yamltestkit.selftest.app.ads;

import com.yamltestkit.selftest.app.AuditService;
import io.vavr.control.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Serves an ad request (imitates the {@code /is} flow): a request whose placement can't be read is rejected; any
 * other is answered {@code 200}, with no ads and a warning when a stage fails. A served response is recorded with
 * {@link AuditService} and logged as events.
 */
@Service
@RequiredArgsConstructor
public class AdService {

    private final AdContextFactory contextFactory;
    private final AdSelector adSelector;
    private final AdRenderer renderer;
    private final AuditService auditService;
    private final AdEventPublisher eventPublisher;

    public RenderedAds serve(AdQuery query) {
        Validation<String, AdContext> context = contextFactory.create(query);
        if (context.isInvalid()) {
            return renderer.rejected(context.getError());
        }
        AdSelection selection = adSelector.select(context.get());
        RenderedAds rendered = renderer.render(context.get(), selection);
        auditService.recordResponse(query.endpoint(), selection.ads().size());
        eventPublisher.publish(context.get(), selection, rendered.body());
        return rendered;
    }
}
