package com.yamltestkit.selftest.unit;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Calls the partner API several times and records each outcome, imitating a retrying client. */
@RequiredArgsConstructor
public class ResilientCaller {

    private final PartnerApi partnerApi;

    public List<String> call(String id, int times) {
        List<String> outcomes = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            try {
                outcomes.add("ok:" + partnerApi.fetch(id));
            } catch (ApiException e) {
                Throwable cause = e.getCause();
                outcomes.add("failed:" + e.getCode() + ":" + e.getMessage() + ":"
                        + (cause == null ? "-" : cause.getClass().getSimpleName() + "(" + cause.getMessage() + ")"));
            }
        }
        return outcomes;
    }
}
