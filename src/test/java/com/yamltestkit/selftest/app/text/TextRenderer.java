package com.yamltestkit.selftest.app.text;

import com.yamltestkit.selftest.app.discover.DiscoverResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders the non-JSON feed listing and the fixed fallback response of {@link TextController}. */
@Component
@RequiredArgsConstructor
public class TextRenderer {

    private static final DiscoverResponse FALLBACK = new DiscoverResponse("3.0", "golden",
            List.of(new DiscoverResponse.Item("fallback-1", "Running shoes")));

    private final JsonMapper jsonMapper;

    /** One {@code <ad>} per feed row, its {@code <url>} carrying the URL-encoded query term. */
    public String feedListing(String queryTerm, List<Map<String, Object>> rows) {
        String encoded = URLEncoder.encode(queryTerm, StandardCharsets.UTF_8);
        return rows.stream()
                .map(row -> "<ad id=\"" + row.get("id") + "\"><url>https://feeds.example/" + row.get("id") + "?qt="
                        + encoded + "</url></ad>")
                .collect(Collectors.joining("", "<ads>", "</ads>"));
    }

    public String fallback() {
        return jsonMapper.writeValueAsString(FALLBACK);
    }
}
