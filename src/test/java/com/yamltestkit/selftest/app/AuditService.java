package com.yamltestkit.selftest.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A real bean the cases spy on (imitates {@code dataLoggingService} and {@code MetricService}): its methods run for
 * real, and {@link #recordResponse} leaves an INFO event a case can see.
 */
@Slf4j
@Component
public class AuditService {

    public String label(String name) {
        return "audit:" + name;
    }

    public void recordResponse(String endpoint, int adCount) {
        log.info("{} served {} ads", endpoint, adCount);
    }
}
