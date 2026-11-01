package com.yamltestkit.infrastructure.wiremock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.CheckResult;
import com.yamltestkit.domain.assertion.ExpectedValueResolver;
import com.yamltestkit.domain.model.AssertOperator;
import com.yamltestkit.domain.model.Assertion;
import com.yamltestkit.domain.model.CallCount;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.VerifyCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Journal verification against a real WireMock server: what the request journal holds after real HTTP requests.
 */
class JournalVerifierIT {

    private static final String LABEL = "cases/_context.yaml";
    private static final String WHERE = "cases/a.yaml, case 'c', step 'main': verify[0] service 'partner'";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final RequestMatcher POST_BID = new RequestMatcher("POST", "/bid", null, Map.of(), Map.of(),
            MissingNode.getInstance());
    private static final Assertion THREE_IMPS = new Assertion("$[0].body.imp", false, null,
            Map.of(AssertOperator.SIZE, IntNode.valueOf(3)), null, null);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private AssertionEvaluator mockAssertions;
    private ExpectedValueResolver mockExpected;
    private WireMockServices wireMock;

    private JournalVerifier underTest;

    @BeforeEach
    void setUp() {
        mockAssertions = mock(AssertionEvaluator.class);
        mockExpected = mock(ExpectedValueResolver.class);
        wireMock = WireMockServices.start(List.of("partner"), LABEL);
        underTest = new JournalVerifier(mockAssertions);
    }

    @AfterEach
    void tearDown() {
        wireMock.close();
    }

    @Test
    void verify_whenTheCountHoldsAndTheCheckAsserts_shouldEvaluateTheAssertsOnTheCapturedRequests() throws Exception {
        post("/bid?site=a&tag=x&tag=y", "{\"imp\": [1, 2, 3]}");
        ArgumentCaptor<JsonNode> captured = ArgumentCaptor.forClass(JsonNode.class);

        CheckResult result = underTest.verify(check(POST_BID, new CallCount.Times(1), "bid", List.of(THREE_IMPS)),
                wireMock, mockExpected, WHERE);

        assertThat(result.passed()).isTrue();
        verify(mockAssertions).evaluate(eq(List.of(THREE_IMPS)), captured.capture(), eq(mockExpected), eq(WHERE));
        JsonNode request = captured.getValue().get(0);
        assertThat(captured.getValue()).hasSize(1);
        assertThat(request.get("method").asText()).isEqualTo("POST");
        assertThat(request.get("path").asText()).isEqualTo("/bid");
        assertThat(request.get("query")).isEqualTo(json("{\"site\": \"a\", \"tag\": [\"x\", \"y\"]}"));
        assertThat(request.get("headers").get("x-test").asText()).isEqualTo("hello");
        assertThat(request.get("body")).isEqualTo(json("{\"imp\": [1, 2, 3]}"));
        assertThat(result.captured()).isEqualTo(captured.getValue());
    }

    @Test
    void verify_whenTheBodyIsNotJsonOrEmpty_shouldCaptureTextOrNull() throws Exception {
        post("/bid", "plain text");
        post("/bid", "");

        CheckResult result = underTest.verify(check(POST_BID, new CallCount.Times(2), "bids", List.of()), wireMock,
                mockExpected, WHERE);

        assertThat(result.captured()).extracting(request -> request.get("body"))
                .containsExactly(json("\"plain text\""), json("null"));
    }

    @Test
    void verify_whenTheCountDiffers_shouldFailListingTheRequestsReceived() throws Exception {
        post("/other", "{}");
        AssertionFailedError assertFailure = new AssertionFailedError(WHERE + ": assert `$[0].body.imp` size");
        when(mockAssertions.evaluate(List.of(THREE_IMPS), json("[]"), mockExpected, WHERE))
                .thenReturn(List.of(assertFailure));

        CheckResult result = underTest.verify(check(POST_BID, new CallCount.Times(1), null, List.of(THREE_IMPS)),
                wireMock, mockExpected, WHERE);

        assertThat(result.failures()).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": expected times: 1, actual: 0 matching requests; requests received: [POST /other]",
                assertFailure.getMessage());
    }

    @Test
    void verify_whenTheCheckNeitherAssertsNorCaptures_shouldNotBuildTheJson() throws Exception {
        post("/bid", "{}");

        CheckResult result = underTest.verify(check(POST_BID, CallCount.AT_LEAST_ONCE, null, List.of()), wireMock,
                mockExpected, WHERE);

        assertThat(result).isEqualTo(new CheckResult(List.of(), null));
        verify(mockAssertions, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void verify_whenTheServiceIsNotDeclared_shouldFailNamingTheDeclaredOnes() {
        VerifyCheck.OnService geo = new VerifyCheck.OnService("geo", POST_BID, new CallCount.Times(1), null, null,
                List.of());

        assertThatThrownBy(() -> underTest.verify(geo, wireMock, mockExpected, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": unknown service 'geo'; cases/_context.yaml declares [partner]");
    }

    @Test
    void verify_whenAHeaderNameIsSentInMixedCase_shouldCaptureItInLowercase() throws Exception {
        post("/bid", "{}", "X-Test");

        CheckResult result = underTest.verify(check(POST_BID, new CallCount.Times(1), "bid", List.of()), wireMock,
                mockExpected, WHERE);

        JsonNode headers = result.captured().get(0).get("headers");
        assertThat(headers.get("x-test").asText()).isEqualTo("hello");
        assertThat(headers.has("X-Test")).isFalse();
    }

    @Test
    void verify_whenTheCheckMatchesALowercaseHeaderName_shouldCountTheRequestsSendingItInMixedCase() throws Exception {
        post("/bid", "{}", "X-Test");
        RequestMatcher withHeader = new RequestMatcher("POST", "/bid", null, Map.of(), Map.of("x-test", "hello"),
                MissingNode.getInstance());

        CheckResult result = underTest.verify(check(withHeader, new CallCount.Times(1), null, List.of()), wireMock,
                mockExpected, WHERE);

        assertThat(result.passed()).isTrue();
    }

    private void post(String pathAndQuery, String body) throws IOException, InterruptedException {
        post(pathAndQuery, body, "x-test");
    }

    /** Sends HTTP/1.1, which keeps the header name's case on the wire (HTTP/2 sends header names in lowercase). */
    private void post(String pathAndQuery, String body, String headerName) throws IOException, InterruptedException {
        URI uri = URI.create(wireMock.resolve("${wiremock.partner.url}", WHERE) + pathAndQuery);
        client.send(HttpRequest.newBuilder(uri).version(HttpClient.Version.HTTP_1_1).timeout(TIMEOUT)
                .header(headerName, "hello").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static VerifyCheck.OnService check(RequestMatcher request, CallCount count, String as,
                                               List<Assertion> asserts) {
        return new VerifyCheck.OnService("partner", request, count, null, as, asserts);
    }
}
