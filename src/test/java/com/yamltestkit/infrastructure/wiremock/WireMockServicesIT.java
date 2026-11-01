package com.yamltestkit.infrastructure.wiremock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.HttpStub;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.StubResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WireMock services against real servers: placeholders, stub translation as WireMock serves it, resets.
 */
class WireMockServicesIT {

    private static final String LABEL = "cases/_context.yaml";
    private static final String WHERE = "cases/a.yaml, case 'c'";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final JsonNode NO_BODY = MissingNode.getInstance();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private WireMockServices underTest;

    @BeforeEach
    void setUp() {
        underTest = WireMockServices.start(List.of("partner", "tracking"), LABEL);
    }

    @AfterEach
    void tearDown() {
        underTest.close();
    }

    @Test
    void resolve_whenValueHasPlaceholders_shouldReplaceThemWithTheServicesAddress() {
        int port = underTest.server("tracking", WHERE).port();

        String result = underTest.resolve("${wiremock.tracking.host}:${wiremock.tracking.port} ${other}", WHERE);

        assertThat(result).isEqualTo("localhost:" + port + " ${other}");
        assertThat(underTest.resolve("${wiremock.partner.url}", WHERE))
                .isEqualTo("http://localhost:" + underTest.server("partner", WHERE).port());
    }

    @Test
    void start_whenServicesAreDeclared_shouldBindEachServerToLocalhostOnly() {
        String result = underTest.server("partner", WHERE).getOptions().bindAddress();

        assertThat(result).isEqualTo("localhost");
        assertThat(underTest.server("tracking", WHERE).getOptions().bindAddress()).isEqualTo("localhost");
    }

    @Test
    void resolve_whenAPlaceholderNamesAnUnknownService_shouldFailNamingTheDeclaredOnes() {
        assertThatThrownBy(() -> underTest.resolve("${wiremock.geo.url}", WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": unknown service 'geo'; cases/_context.yaml declares [partner, tracking]");
    }

    @Test
    void register_whenStubIsASequence_shouldServeTheResponsesInOrderAndRepeatTheLast() throws Exception {
        underTest.register(stub(List.of(response(503, NO_BODY, null, false), response(200, TextNode.valueOf("ok"),
                null, false)), true), WHERE);

        List<Integer> result = List.of(get("/bid").statusCode(), get("/bid").statusCode(), get("/bid").statusCode());

        assertThat(result).containsExactly(503, 200, 200);
    }

    @Test
    void register_whenResponseIsTemplated_shouldRenderItFromTheRequest() throws Exception {
        underTest.register(stub(List.of(response(200, TextNode.valueOf("id={{request.query.id}}"), null, true)),
                false), WHERE);

        HttpResponse<String> result = get("/bid?id=42");

        assertThat(result.body()).isEqualTo("id=42");
    }

    @Test
    void register_whenResponseIsAFault_shouldResetTheConnection() {
        underTest.register(stub(List.of(response(200, NO_BODY, "CONNECTION_RESET_BY_PEER", false)), false), WHERE);

        assertThatThrownBy(() -> get("/bid")).isInstanceOf(IOException.class);
    }

    @Test
    void register_whenServiceIsUnknown_shouldFailNamingTheDeclaredOnes() {
        HttpStub geo = new HttpStub("geo", new RequestMatcher("GET", "/x", null, Map.of(), Map.of(), NO_BODY),
                List.of(response(200, NO_BODY, null, false)), false);

        assertThatThrownBy(() -> underTest.register(geo, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": unknown service 'geo'; cases/_context.yaml declares [partner, tracking]");
    }

    @Test
    void resetAll_whenStubsAndRequestsExist_shouldRemoveBoth() throws Exception {
        underTest.register(stub(List.of(response(200, NO_BODY, null, false)), false), WHERE);
        get("/bid");

        underTest.resetAll();

        assertThat(underTest.server("partner", WHERE).getAllServeEvents()).isEmpty();
        assertThat(get("/bid").statusCode()).isEqualTo(404);
    }

    @Test
    void resetRequests_whenStubsAndRequestsExist_shouldKeepTheStubs() throws Exception {
        underTest.register(stub(List.of(response(200, NO_BODY, null, false)), false), WHERE);
        get("/bid");

        underTest.resetRequests();

        assertThat(underTest.server("partner", WHERE).getAllServeEvents()).isEmpty();
        assertThat(get("/bid").statusCode()).isEqualTo(200);
    }

    @Test
    void find_whenRequestsWereReceived_shouldReturnTheMatchingOnesInTheOrderReceived() throws Exception {
        get("/bid?id=1");
        get("/other");
        get("/bid?id=2");

        List<LoggedRequest> result = underTest.find("partner", new RequestMatcher("GET", "/bid", null, Map.of(),
                Map.of(), NO_BODY), WHERE);

        assertThat(result).extracting(LoggedRequest::getUrl).containsExactly("/bid?id=1", "/bid?id=2");
        assertThat(underTest.received("partner", WHERE)).extracting(LoggedRequest::getUrl)
                .containsExactly("/bid?id=1", "/other", "/bid?id=2");
    }

    @Test
    void find_whenTheMatcherHasQueryAndHeaders_shouldMatchOnThemToo() throws Exception {
        get("/bid?id=1", "a");
        get("/bid?id=2", "a");
        get("/bid?id=2", "b");

        List<LoggedRequest> result = underTest.find("partner", new RequestMatcher(null, null, "/b.d", Map.of("id", "2"),
                Map.of("x-test", "b"), NO_BODY), WHERE);

        assertThat(result).extracting(request -> request.getHeader("x-test")).containsExactly("b");
    }

    @Test
    void register_whenTheStubMatchesALowercaseHeaderName_shouldMatchItSentInMixedCase() throws Exception {
        underTest.register(new HttpStub("partner", new RequestMatcher("GET", "/bid", null, Map.of(),
                Map.of("x-test", "a"), NO_BODY), List.of(response(200, NO_BODY, null, false)), false), WHERE);

        HttpResponse<String> result = get("/bid", "X-Test", "a");

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(get("/bid", "X-Test", "b").statusCode()).isEqualTo(404);
    }

    @Test
    void find_whenTheMatcherHasALowercaseHeaderName_shouldMatchItSentInMixedCase() throws Exception {
        get("/bid", "X-Test", "a");
        get("/bid", "X-Test", "b");

        List<LoggedRequest> result = underTest.find("partner", new RequestMatcher("GET", "/bid", null, Map.of(),
                Map.of("x-test", "a"), NO_BODY), WHERE);

        assertThat(result).extracting(request -> request.getHeader("x-test")).containsExactly("a");
    }

    private HttpResponse<String> get(String pathAndQuery) throws IOException, InterruptedException {
        return get(pathAndQuery, "none");
    }

    private HttpResponse<String> get(String pathAndQuery, String testHeader) throws IOException, InterruptedException {
        return get(pathAndQuery, "x-test", testHeader);
    }

    /** Sends HTTP/1.1, which keeps the header name's case on the wire (HTTP/2 sends header names in lowercase). */
    private HttpResponse<String> get(String pathAndQuery, String headerName, String headerValue)
            throws IOException, InterruptedException {
        URI uri = URI.create(underTest.resolve("${wiremock.partner.url}", WHERE) + pathAndQuery);
        return client.send(HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_1_1).timeout(TIMEOUT)
                .header(headerName, headerValue).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpStub stub(List<StubResponse> responses, boolean sequence) {
        return new HttpStub("partner", new RequestMatcher("GET", "/bid", null, Map.of(), Map.of(), NO_BODY),
                responses, sequence);
    }

    private static StubResponse response(int status, JsonNode body, String fault, boolean template) {
        return new StubResponse(status, Map.of(), body, 0, fault, template);
    }
}
