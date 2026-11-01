package com.yamltestkit.infrastructure.wiremock;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.ContentPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.yamltestkit.domain.model.HttpStub;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.StubResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Translates an {@code http} stub into WireMock mappings: request matching ({@code method}, {@code path} /
 * {@code pathPattern}, {@code query}, {@code headers}, JSON {@code body}) and the response ({@code status},
 * {@code headers}, {@code body}, {@code delayMs}, {@code fault}, {@code template}). A {@code sequence} becomes one
 * mapping per response in a WireMock scenario, each moving the scenario on, the last one staying in its state so it
 * repeats.
 */
final class WireMockStubMapper {

    static final String TEMPLATE_TRANSFORMER = "response-template";
    private static final String ANY_METHOD = "ANY";

    private WireMockStubMapper() {
    }

    /** @param scenario the scenario name of a {@code sequence}, unique among the server's stubs */
    static List<MappingBuilder> mappings(HttpStub stub, String scenario) {
        if (!stub.sequence()) {
            return List.of(request(stub.request()).willReturn(response(stub.responses().getFirst())));
        }
        List<MappingBuilder> mappings = new ArrayList<>();
        String state = Scenario.STARTED;
        for (int i = 0; i < stub.responses().size(); i++) {
            boolean last = i == stub.responses().size() - 1;
            String next = "response " + (i + 1);
            ScenarioMappingBuilder mapping = request(stub.request()).inScenario(scenario)
                    .whenScenarioStateIs(state)
                    .willReturn(response(stub.responses().get(i)));
            mappings.add(last ? mapping : mapping.willSetStateTo(next));
            state = next;
        }
        return mappings;
    }

    /** The same request matching as a stub's, for reading the request journal. */
    static RequestPatternBuilder requestPattern(RequestMatcher matcher) {
        RequestPatternBuilder pattern = RequestPatternBuilder.newRequestPattern(RequestMethod.fromString(
                method(matcher)), url(matcher));
        addMatching(matcher, pattern::withQueryParam, pattern::withHeader, pattern::withRequestBody);
        return pattern;
    }

    private static MappingBuilder request(RequestMatcher matcher) {
        MappingBuilder mapping = WireMock.request(method(matcher), url(matcher));
        addMatching(matcher, mapping::withQueryParam, mapping::withHeader, mapping::withRequestBody);
        return mapping;
    }

    /** Query, headers and JSON body matching, shared by stub mappings and journal patterns. */
    private static void addMatching(RequestMatcher matcher, BiConsumer<String, StringValuePattern> query,
                                    BiConsumer<String, StringValuePattern> header,
                                    Consumer<ContentPattern<?>> body) {
        matcher.query().forEach((name, value) -> query.accept(name, WireMock.equalTo(value)));
        matcher.headers().forEach((name, value) -> header.accept(name, WireMock.equalTo(value)));
        if (!matcher.body().isMissingNode()) {
            body.accept(WireMock.equalToJson(matcher.body().toString()));
        }
    }

    private static String method(RequestMatcher matcher) {
        return matcher.method() == null ? ANY_METHOD : matcher.method();
    }

    private static UrlPattern url(RequestMatcher matcher) {
        if (matcher.path() != null) {
            return WireMock.urlPathEqualTo(matcher.path());
        }
        return matcher.pathPattern() != null ? WireMock.urlPathMatching(matcher.pathPattern()) : WireMock.anyUrl();
    }

    private static ResponseDefinitionBuilder response(StubResponse response) {
        ResponseDefinitionBuilder builder = WireMock.aResponse().withStatus(response.status());
        response.headers().forEach(builder::withHeader);
        if (!response.body().isMissingNode()) {
            builder.withBody(text(response.body()));
        }
        if (response.delayMs() > 0) {
            builder.withFixedDelay(response.delayMs());
        }
        if (response.fault() != null) {
            builder.withFault(Fault.valueOf(response.fault()));
        }
        if (response.template()) {
            builder.withTransformers(TEMPLATE_TRANSFORMER);
        }
        return builder;
    }

    /** A text body as written (e.g. from a non-JSON file), any other value as JSON. */
    private static String text(JsonNode body) {
        return body.isTextual() ? body.asText() : body.toString();
    }
}
