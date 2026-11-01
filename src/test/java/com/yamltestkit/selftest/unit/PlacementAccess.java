package com.yamltestkit.selftest.unit;

import com.google.protobuf.Struct;
import io.vavr.control.Validation;

/** Imitates a placement reader: a protobuf message wrapped in a vavr {@code Validation}. */
public interface PlacementAccess {

    Validation<String, Struct> getPlacement(long id);
}
