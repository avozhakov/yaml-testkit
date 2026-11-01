package com.yamltestkit.infrastructure.wiremock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.yamltestkit.domain.json.JsonMappers;
import com.yamltestkit.domain.model.HttpStub;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.StubResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WireMockStubMapperTest {

    private static final JsonNode BID_BODY = JsonNodeFactory.instance.objectNode().put("imp", 3);
    private static final JsonNode OK_BODY = JsonNodeFactory.instance.objectNode().put("ok", true);
    private static final RequestMatcher POST_BID = new RequestMatcher("POST", "/bid", null, Map.of("v", "2"),
            Map.of("X-Key", "k"), BID_BODY);

    @Test
    void mappings_whenStubHasOneResponse_shouldMatchTheRequestAndAnswerWithTheResponse() throws Exception {
        HttpStub stub = new HttpStub("partner", POST_BID, List.of(new StubResponse(201, Map.of("X-Id", "9"),
                OK_BODY, 300, null, true)), false);

        List<MappingBuilder> result = WireMockStubMapper.mappings(stub, "s");

        assertThat(result).hasSize(1);
        StubMapping mapping = result.getFirst().build();
        assertThat(mapping.getRequest().getMethod()).isEqualTo(RequestMethod.POST);
        assertThat(mapping.getRequest().getUrlPath()).isEqualTo("/bid");
        assertThat(mapping.getRequest().getQueryParameters().get("v").getExpected()).isEqualTo("2");
        assertThat(mapping.getRequest().getHeaders().get("X-Key").getExpected()).isEqualTo("k");
        assertThat(JsonMappers.plain().readTree(mapping.getRequest().getBodyPatterns().getFirst().getExpected()))
                .isEqualTo(BID_BODY);
        assertThat(mapping.getResponse().getStatus()).isEqualTo(201);
        assertThat(mapping.getResponse().getHeaders().getHeader("X-Id").firstValue()).isEqualTo("9");
        assertThat(mapping.getResponse().getBody()).isEqualTo("{\"ok\":true}");
        assertThat(mapping.getResponse().getFixedDelayMilliseconds()).isEqualTo(300);
        assertThat(mapping.getResponse().getTransformers()).containsExactly(WireMockStubMapper.TEMPLATE_TRANSFORMER);
        assertThat(mapping.getScenarioName()).isNull();
    }

    @Test
    void mappings_whenRequestGivesNoMethodAndAPathPattern_shouldMatchAnyMethodOnThePattern() {
        HttpStub stub = new HttpStub("partner", new RequestMatcher(null, null, "/items/.*", Map.of(), Map.of(),
                MissingNode.getInstance()), List.of(new StubResponse(200, Map.of(), TextNode.valueOf("<xml/>"), 0,
                null, false)), false);

        StubMapping result = WireMockStubMapper.mappings(stub, "s").getFirst().build();

        assertThat(result.getRequest().getMethod()).isEqualTo(RequestMethod.ANY);
        assertThat(result.getRequest().getUrlPathPattern()).isEqualTo("/items/.*");
        assertThat(result.getRequest().getBodyPatterns()).isNull();
        assertThat(result.getResponse().getBody()).isEqualTo("<xml/>");
        assertThat(result.getResponse().getTransformers()).isNull();
    }

    @Test
    void mappings_whenResponseIsAFault_shouldAnswerWithTheFault() {
        HttpStub stub = new HttpStub("partner", POST_BID, List.of(new StubResponse(200, Map.of(),
                MissingNode.getInstance(), 0, "CONNECTION_RESET_BY_PEER", false)), false);

        StubMapping result = WireMockStubMapper.mappings(stub, "s").getFirst().build();

        assertThat(result.getResponse().getFault()).isEqualTo(Fault.CONNECTION_RESET_BY_PEER);
        assertThat(result.getResponse().getBody()).isNull();
    }

    @Test
    void mappings_whenStubIsASequence_shouldChainTheResponsesInAScenarioWhoseLastStateStays() {
        HttpStub stub = new HttpStub("partner", POST_BID, List.of(
                new StubResponse(503, Map.of(), MissingNode.getInstance(), 0, null, false),
                new StubResponse(502, Map.of(), MissingNode.getInstance(), 0, null, false),
                new StubResponse(200, Map.of(), OK_BODY, 0, null, false)), true);

        List<StubMapping> result = WireMockStubMapper.mappings(stub, "yamlit-1").stream()
                .map(MappingBuilder::build)
                .toList();

        assertThat(result).extracting(StubMapping::getScenarioName).containsOnly("yamlit-1");
        assertThat(result).extracting(StubMapping::getRequiredScenarioState)
                .containsExactly(Scenario.STARTED, "response 1", "response 2");
        assertThat(result).extracting(StubMapping::getNewScenarioState)
                .containsExactly("response 1", "response 2", null);
        assertThat(result).extracting(mapping -> mapping.getResponse().getStatus()).containsExactly(503, 502, 200);
    }
}
