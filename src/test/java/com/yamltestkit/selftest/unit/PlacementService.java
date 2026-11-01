package com.yamltestkit.selftest.unit;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.vavr.control.Validation;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PlacementService {

    private static final String SLOTS = "availableSlots";

    private final PlacementAccess placementAccess;

    public int availableSlots(long id) {
        return placementAccess.getPlacement(id)
                .map(placement -> placement.getFieldsOrDefault(SLOTS, Value.getDefaultInstance()))
                .map(slots -> (int) slots.getNumberValue())
                .getOrElse(-1);
    }

    public Validation<String, Struct> placement(long id) {
        return placementAccess.getPlacement(id);
    }

    public static String name(Struct placement) {
        return placement.getFieldsOrThrow("name").getStringValue();
    }
}
