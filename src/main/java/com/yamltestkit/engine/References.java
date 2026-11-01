package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.assertion.JsonPaths;
import com.yamltestkit.domain.assertion.JsonTexts;
import com.yamltestkit.domain.model.ValueRef;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * The values {@code { ref, path?, parseJson? }} expected values point to (design D9): step results and
 * {@code verify[].as} captures of one case, by name. A step result is computed only when a reference reads it, so a
 * result nobody references is never converted. That every reference names an earlier producer is checked when the
 * case is loaded; a reference that can't be read here (the step threw, the path is missing) is an assertion failure.
 */
final class References {

    private final Map<String, Supplier<JsonNode>> values = new HashMap<>();

    /**
     * Keeps a value under a step or capture name, replacing an earlier one (a polled step records each attempt).
     *
     * @param value computed on the first read; a {@link RuntimeException} it throws says why there is no value
     */
    void put(String name, Supplier<JsonNode> value) {
        values.put(name, new Once(value));
    }

    /**
     * The value itself, or the value a {@code { ref }} points to.
     *
     * @param where the case file and case, prefixed to failure messages
     * @throws AssertionFailedError when the referenced value can't be produced, its {@code path} is missing or its
     *                              {@code parseJson} value is not JSON
     */
    JsonNode resolve(JsonNode expected, String where) {
        if (!ValueRef.isRef(expected)) {
            return expected;
        }
        ValueRef ref = ValueRef.of(expected);
        JsonNode value = value(ref, where);
        if (ref.path() != null) {
            JsonPaths.PathResult selected = JsonPaths.read(value, ref.path());
            if (!selected.found()) {
                throw failure(where, ref, "path `" + ref.path() + "`: " + selected.missingReason() + " in "
                        + abbreviate(value), null);
            }
            value = selected.value();
        }
        if (!ref.parseJson()) {
            return value;
        }
        try {
            return JsonTexts.parse(value);
        } catch (IllegalArgumentException e) {
            throw failure(where, ref, "parseJson: " + e.getMessage(), e);
        }
    }

    private JsonNode value(ValueRef ref, String where) {
        Supplier<JsonNode> value = values.get(ref.name());
        if (value == null) {
            throw failure(where, ref, "nothing was recorded under this name", null);
        }
        try {
            return value.get();
        } catch (RuntimeException e) {
            throw failure(where, ref, "no value: " + e.getMessage(), e);
        }
    }

    private static AssertionFailedError failure(String where, ValueRef ref, String reason, @Nullable Throwable cause) {
        return new AssertionFailedError(where + ": reference '" + ref.name() + "': " + reason, cause);
    }

    /** Computes the value on the first read and keeps it; a case runs on one thread. */
    @RequiredArgsConstructor
    private static final class Once implements Supplier<JsonNode> {

        private final Supplier<JsonNode> compute;
        private @Nullable JsonNode value;

        @Override
        public JsonNode get() {
            if (value == null) {
                value = compute.get();
            }
            return value;
        }
    }
}
