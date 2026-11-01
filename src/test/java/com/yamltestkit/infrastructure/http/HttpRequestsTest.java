package com.yamltestkit.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.Query;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestsTest {

    private static final int PORT = 8123;
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final JsonNode JSON_BODY = JsonNodeFactory.instance.objectNode().put("maxAds", 4);

    @Test
    void uri_whenQueryHasParameters_shouldEncodeThemWithSpacesAsPercent20() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("plid", List.of("123"));
        params.put("qt", List.of("shoes & socks"));
        params.put("tag", List.of("a", "b"));
        Call.Http call = new Call.Http("GET", "/is", new Query.Params(params), Map.of(), MissingNode.getInstance());

        URI result = HttpRequests.uri(call, PORT);

        assertThat(result).hasToString("http://localhost:8123/is?plid=123&qt=shoes%20%26%20socks&tag=a&tag=b");
    }

    @Test
    void uri_whenQueryIsRaw_shouldSendItAsWritten() {
        Call.Http call = new Call.Http("GET", "/is", new Query.Raw("plid=123&qt=%5B%22a%22%5D"), Map.of(),
                MissingNode.getInstance());

        URI result = HttpRequests.uri(call, PORT);

        assertThat(result.getRawQuery()).isEqualTo("plid=123&qt=%5B%22a%22%5D");
    }

    @Test
    void uri_whenThereIsNoQuery_shouldLeaveOutTheQuestionMark() {
        Call.Http call = new Call.Http("GET", "/health", Query.NONE, Map.of(), MissingNode.getInstance());

        URI result = HttpRequests.uri(call, PORT);

        assertThat(result).hasToString("http://localhost:8123/health");
    }

    @Test
    void request_whenBodyIsJson_shouldSendItAsJsonWithHeaders() {
        Call.Http call = new Call.Http("POST", "/di", Query.NONE, Map.of("x-discover-version", "3.0"), JSON_BODY);

        HttpRequest result = HttpRequests.request(call, PORT, TIMEOUT);

        assertThat(result.method()).isEqualTo("POST");
        assertThat(result.timeout()).contains(TIMEOUT);
        assertThat(result.headers().firstValue("x-discover-version")).contains("3.0");
        assertThat(result.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(result.bodyPublisher().map(HttpRequest.BodyPublisher::contentLength))
                .contains((long) "{\"maxAds\":4}".length());
    }

    @Test
    void request_whenTheCaseSetsTheContentType_shouldKeepIt() {
        Call.Http call = new Call.Http("POST", "/di", Query.NONE, Map.of("content-type", "application/x-protobuf"),
                JSON_BODY);

        HttpRequest result = HttpRequests.request(call, PORT, TIMEOUT);

        assertThat(result.headers().allValues("Content-Type")).containsExactly("application/x-protobuf");
    }

    @Test
    void request_whenBodyIsText_shouldSendItWithoutAContentType() {
        Call.Http call = new Call.Http("PUT", "/text", Query.NONE, Map.of(), TextNode.valueOf("plain"));

        HttpRequest result = HttpRequests.request(call, PORT, TIMEOUT);

        assertThat(result.headers().firstValue("Content-Type")).isEqualTo(Optional.empty());
        assertThat(result.bodyPublisher().map(HttpRequest.BodyPublisher::contentLength)).contains(5L);
    }

    @Test
    void request_whenThereIsNoBody_shouldSendAnEmptyBody() {
        Call.Http call = new Call.Http("GET", "/is", Query.NONE, Map.of(), MissingNode.getInstance());

        HttpRequest result = HttpRequests.request(call, PORT, TIMEOUT);

        assertThat(result.bodyPublisher().map(HttpRequest.BodyPublisher::contentLength)).contains(0L);
    }
}
