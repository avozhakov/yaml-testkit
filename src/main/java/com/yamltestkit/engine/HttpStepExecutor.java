package com.yamltestkit.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.BodyAssertion;
import com.yamltestkit.domain.assertion.Failures;
import com.yamltestkit.domain.assertion.TextAssertion;
import com.yamltestkit.domain.json.JsonMappers;
import com.yamltestkit.domain.model.BodyExpectation;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.infrastructure.http.HttpRequests;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * Runs a {@code call.http} step: sends the request to the case's application over its real port (so asynchronous
 * controllers need no special handling), keeps the body as the step's result for references, runs the
 * {@link EffectChecks} ({@code verify}, {@code logs}, {@code context}) and checks {@code status}, {@code headers}
 * (exact value of each named header), {@code body}, {@code bodyText} and {@code assert}, together with any error a
 * stub raised meanwhile.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class HttpStepExecutor implements StepExecutor {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient client;
    private final BodyAssertion bodies;
    private final TextAssertion texts;
    private final AssertionEvaluator assertions;
    private final EffectChecks effects;

    public static HttpStepExecutor create(EffectChecks effects) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        return new HttpStepExecutor(client, new BodyAssertion(), new TextAssertion(), new AssertionEvaluator(),
                effects);
    }

    @Override
    public void execute(CaseRun run, Step step) throws IOException, InterruptedException {
        Call.Http call = (Call.Http) step.call();
        String where = run.where(step);
        int port = run.caseContext().running().requirePort(where);
        HttpResponse<String> response = client.send(HttpRequests.request(call, port, REQUEST_TIMEOUT),
                HttpResponse.BodyHandlers.ofString());
        run.recordResult(step, () -> result(response.body()));
        List<AssertionFailedError> failures = new ArrayList<>(run.stubErrors().drain());
        failures.addAll(effects.check(run, step));
        failures.addAll(check(step.expect(), response, run, where));
        Failures.throwIfAny(failures, where);
    }

    /** Closes the HTTP client, which waits for requests in flight. */
    @Override
    public void close() {
        client.close();
    }

    private List<AssertionFailedError> check(Expect expect, HttpResponse<String> response, CaseRun run,
                                             String where) {
        List<AssertionFailedError> failures = new ArrayList<>();
        if (expect.status() != null && expect.status() != response.statusCode()) {
            failures.add(new AssertionFailedError(where + ": status expected " + expect.status() + " but was "
                    + response.statusCode() + "; body: " + abbreviate(response.body()), expect.status(),
                    response.statusCode()));
        }
        expect.headers().forEach((name, expected) -> header(response, name, expected, where)
                .ifPresent(failures::add));
        if (expect.body() != null) {
            failures.addAll(body(expect.body(), response.body(), run, where));
        }
        if (expect.bodyText() != null) {
            failures.addAll(texts.check(expect.bodyText(), response.body(), where));
        }
        if (!expect.asserts().isEmpty()) {
            failures.addAll(asserts(expect, response.body(), run, where));
        }
        return failures;
    }

    /** A reference the body expectation can't read fails that expectation only, reported with the others. */
    private List<AssertionFailedError> body(BodyExpectation body, String actual, CaseRun run, String where) {
        JsonNode expected;
        try {
            expected = run.resolve(body.expected());
        } catch (AssertionFailedError unreadableReference) {
            return List.of(unreadableReference);
        }
        return bodies.check(body, expected, actual, where);
    }

    private static Optional<AssertionFailedError> header(HttpResponse<String> response, String name, String expected,
                                                         String where) {
        List<String> values = response.headers().allValues(name);
        if (values.isEmpty()) {
            return Optional.of(new AssertionFailedError(where + ": header " + name + " expected \"" + expected
                    + "\" but the response has no such header; headers: " + headerNames(response.headers().map()),
                    expected, null));
        }
        String actual = String.join(",", values);
        return expected.equals(actual) ? Optional.empty() : Optional.of(new AssertionFailedError(where + ": header "
                + name + " expected \"" + expected + "\" but was \"" + actual + "\"", expected, actual));
    }

    private List<AssertionFailedError> asserts(Expect expect, String body, CaseRun run, String where) {
        JsonNode json;
        try {
            json = JsonMappers.plain().readTree(body);
        } catch (JsonProcessingException e) {
            return List.of(new AssertionFailedError(where + ": assert needs a JSON body, but the body is not JSON ("
                    + e.getOriginalMessage() + "): " + abbreviate(body)));
        }
        return assertions.evaluate(expect.asserts(), json, run, where);
    }

    /** What references to the step read: the body as JSON, or as text when it is not JSON. */
    private static JsonNode result(String body) {
        try {
            JsonNode json = JsonMappers.plain().readTree(body);
            return json.isMissingNode() ? TextNode.valueOf(body) : json;
        } catch (JsonProcessingException e) {
            // Not an error: a non-JSON body is referenced as text.
            return TextNode.valueOf(body);
        }
    }

    private static String headerNames(Map<String, List<String>> headers) {
        return String.join(", ", headers.keySet());
    }
}
