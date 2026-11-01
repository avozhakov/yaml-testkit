package com.yamltestkit.selftest.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loaded from {@link FeedRepository} at startup and on {@link #reload()} (imitates the feed provider caches); logs a
 * WARN for each row without a numeric {@code id} (imitates the unmappable-feed warning). A failed load keeps the
 * previous rows, since the query runs before the cache is replaced.
 */
@Slf4j
@Component
public class FeedCache {

    private final FeedRepository repository;
    private volatile List<Map<String, Object>> rows;

    public FeedCache(FeedRepository repository) {
        this.repository = repository;
        this.rows = load();
    }

    public void reload() {
        rows = load();
    }

    public int size() {
        return rows.size();
    }

    public List<Map<String, Object>> rows() {
        return rows;
    }

    public Optional<Map<String, Object>> find(long id) {
        return rows.stream()
                .filter(row -> row.get("id") instanceof Number number && number.longValue() == id)
                .findFirst();
    }

    private List<Map<String, Object>> load() {
        List<Map<String, Object>> loaded = List.copyOf(repository.findAll());
        loaded.stream()
                .filter(row -> !(row.get("id") instanceof Number))
                .forEach(row -> log.warn("unmappable feed row {}", row));
        return loaded;
    }
}
