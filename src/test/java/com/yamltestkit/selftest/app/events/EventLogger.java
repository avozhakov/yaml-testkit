package com.yamltestkit.selftest.app.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Imitates {@code KafkaDataLoggerClient}: the data-logging boundary, called from an executor thread. There is no
 * Kafka in tests, so {@code _context.yaml} mocks it.
 */
@Slf4j
@Component
public class EventLogger {

    public void send(String requestId, LoggedEvent event, String topic) {
        log.info("{} event of request {}", topic, requestId);
    }
}
