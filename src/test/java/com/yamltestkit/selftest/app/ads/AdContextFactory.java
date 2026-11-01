package com.yamltestkit.selftest.app.ads;

import com.google.protobuf.Struct;
import io.vavr.control.Validation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the context of an ad request: placement, query terms after intent filtering, request id. */
@Component
@RequiredArgsConstructor
public class AdContextFactory {

    private final IdGenerator ids;
    private final PlacementAccess placementAccess;
    private final QueryTermParser queryTermParser;
    private final OuterService outerService;

    public Validation<String, AdContext> create(AdQuery query) {
        Validation<String, Struct> placement = placementAccess.getPlacement(query.placementId());
        if (placement.isInvalid()) {
            return Validation.invalid(placement.getError());
        }
        QueryTerms terms = queryTermParser.parse(query.queryTerm());
        return Validation.valid(new AdContext(ids.nextRequestId(), query, outerService.filterTerms(terms.values()),
                terms.multi(), placement.get()));
    }
}
