package com.yamltestkit.infrastructure.wiremock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.tomakehurst.wiremock.http.MultiValue;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.yamltestkit.domain.assertion.AssertionEvaluator;
import com.yamltestkit.domain.assertion.CallCounts;
import com.yamltestkit.domain.assertion.CheckResult;
import com.yamltestkit.domain.assertion.ExpectedValueResolver;
import com.yamltestkit.domain.json.JsonMappers;
import com.yamltestkit.domain.model.VerifyCheck;
import lombok.RequiredArgsConstructor;
import org.opentest4j.AssertionFailedError;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.yamltestkit.domain.json.Abbreviations.abbreviate;

/**
 * A {@code verify} check on a WireMock service (design D8): reads the service's request journal with the check's
 * request matcher (same keys as an {@code http} stub's), checks the number of matching requests, and exposes them as
 * {@code [ { "method", "path", "query", "headers", "body" } ]} in the order received for the nested {@code assert}
 * entries and the {@code as} capture. {@code query} and {@code headers} map each name to its value, or to the array of
 * its values when it was sent more than once; header names are in lowercase, whatever case the client sent;
 * {@code body} is the parsed JSON when the body is JSON, else the text, {@code null} when empty.
 */
@RequiredArgsConstructor
public final class JournalVerifier {

    private final AssertionEvaluator assertions;

    public static JournalVerifier create() {
        return new JournalVerifier(new AssertionEvaluator());
    }

    /**
     * @param where the step and check, prefixed to failure messages
     * @throws com.yamltestkit.domain.model.CaseDefinitionException when the service is not declared
     */
    public CheckResult verify(VerifyCheck.OnService check, WireMockServices wireMock, ExpectedValueResolver expected,
                              String where) {
        List<LoggedRequest> matching = wireMock.find(check.service(), check.request(), where);
        List<AssertionFailedError> failures = new ArrayList<>();
        CallCounts.mismatch(check.count(), matching.size()).ifPresent(mismatch -> failures.add(
                new AssertionFailedError(where + ": " + mismatch + " matching requests; requests received: "
                        + describe(wireMock.received(check.service(), where)))));
        if (check.as() == null && check.asserts().isEmpty()) {
            return new CheckResult(failures, null);
        }
        ArrayNode captured = JsonNodeFactory.instance.arrayNode();
        matching.forEach(request -> captured.add(json(request)));
        failures.addAll(assertions.evaluate(check.asserts(), captured, expected, where));
        return new CheckResult(failures, captured);
    }

    private static ObjectNode json(LoggedRequest request) {
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("method", request.getMethod().getName());
        json.put("path", URI.create(request.getAbsoluteUrl()).getPath());
        ObjectNode query = json.putObject("query");
        request.getQueryParams().values().forEach(parameter -> query.set(parameter.key(), values(parameter)));
        ObjectNode headers = json.putObject("headers");
        request.getHeaders().all().forEach(header -> headers.set(header.key().toLowerCase(Locale.ROOT),
                values(header)));
        json.set("body", body(request.getBodyAsString()));
        return json;
    }

    private static JsonNode values(MultiValue value) {
        if (value.values().size() == 1) {
            return TextNode.valueOf(value.firstValue());
        }
        ArrayNode values = JsonNodeFactory.instance.arrayNode();
        value.values().forEach(values::add);
        return values;
    }

    private static JsonNode body(String body) {
        if (body.isEmpty()) {
            return NullNode.getInstance();
        }
        try {
            return JsonMappers.plain().readTree(body);
        } catch (JsonProcessingException e) {
            // Not an error: a non-JSON body is exposed as text, as documented.
            return TextNode.valueOf(body);
        }
    }

    private static String describe(List<LoggedRequest> requests) {
        if (requests.isEmpty()) {
            return "none";
        }
        return abbreviate(requests.stream()
                .map(request -> request.getMethod().getName() + " " + request.getUrl())
                .collect(Collectors.joining(", ", "[", "]")));
    }
}
