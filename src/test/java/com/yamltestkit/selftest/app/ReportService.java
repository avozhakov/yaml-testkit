package com.yamltestkit.selftest.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportService {

    private final ReportStore reportStore;

    public int count() {
        return reportStore.count();
    }
}
