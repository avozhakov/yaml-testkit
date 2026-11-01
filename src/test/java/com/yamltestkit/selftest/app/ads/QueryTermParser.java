package com.yamltestkit.selftest.app.ads;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** Splits a {@code qt} that is a whole JSON array of strings into its terms; any other value is one term as it is. */
@Component
@RequiredArgsConstructor
public class QueryTermParser {

    private static final TypeReference<List<String>> TERMS = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    public QueryTerms parse(String queryTerm) {
        if (queryTerm.startsWith("[")) {
            try {
                return new QueryTerms(jsonMapper.readValue(queryTerm, TERMS), true);
            } catch (JacksonException notAJsonArray) {
                // "[Solidcore]" is a plain term that only starts like an array: it stays one term, as received
            }
        }
        return new QueryTerms(List.of(queryTerm), false);
    }
}
