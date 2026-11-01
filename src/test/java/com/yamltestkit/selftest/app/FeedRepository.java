package com.yamltestkit.selftest.app;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Stands in for a JDBC repository; the cases always mock it, since there is no database. */
@Component
public class FeedRepository {

    public List<Map<String, Object>> findAll() {
        throw new IllegalStateException("no database in tests");
    }
}
