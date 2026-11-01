package com.yamltestkit.selftest.app.ads;

import com.google.protobuf.Struct;
import io.vavr.control.Validation;
import org.springframework.stereotype.Component;

/**
 * Reads a placement: a protobuf message ({@link Struct} stands in for a generated placement message) wrapped in a
 * vavr {@code Validation}. There is no placement store in tests, so the cases mock it.
 */
@Component
public class PlacementAccess {

    public Validation<String, Struct> getPlacement(long id) {
        return Validation.invalid("no placement store in tests, placement " + id);
    }
}
