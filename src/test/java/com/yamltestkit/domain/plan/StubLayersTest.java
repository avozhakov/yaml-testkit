package com.yamltestkit.domain.plan;

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.BeanEntry;
import com.yamltestkit.domain.model.HttpStub;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.StubResponse;
import com.yamltestkit.domain.model.StubSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StubLayersTest {

    private static final MethodRef GET_PLACEMENT = new MethodRef("getPlacement", null);
    private static final Stub DEFAULT_ANY = stub(GET_PLACEMENT, List.of(new ArgMatcher.Any()), 1);
    private static final Stub DEFAULT_SEVEN = stub(GET_PLACEMENT, List.of(literal("7")), 2);
    private static final Stub CASE_ANY = stub(GET_PLACEMENT, List.of(new ArgMatcher.Any()), 3);
    private static final StubLayers.BeanStub BOOT = new StubLayers.BeanStub("placementAccess",
            stub(GET_PLACEMENT, null, 0), "boot");
    private static final StubLayers.BeanStub DEFAULTS_ANY = new StubLayers.BeanStub("placementAccess", DEFAULT_ANY,
            "defaults");
    private static final StubLayers.BeanStub DEFAULTS_SEVEN = new StubLayers.BeanStub("placementAccess",
            DEFAULT_SEVEN, "defaults");
    private static final StubLayers.BeanStub CASE = new StubLayers.BeanStub("placementAccess", CASE_ANY, "case");
    private static final StubLayers.BeanStub OTHER_BEAN = new StubLayers.BeanStub("providers", DEFAULT_ANY, "case");

    @Test
    void merge_whenALaterLayerHasTheSameKey_shouldDropTheEarlierStubAndRegisterTheLaterOneLast() {
        List<StubLayers.BeanStub> result = StubLayers.merge(List.of(
                List.of(BOOT),
                List.of(DEFAULTS_ANY, DEFAULTS_SEVEN),
                List.of(CASE, OTHER_BEAN)), StubLayers.BeanStub::key);

        assertThat(result).containsExactly(BOOT, DEFAULTS_SEVEN, CASE, OTHER_BEAN);
    }

    @Test
    void merge_whenArgsDiffer_shouldKeepBothStubs() {
        List<StubLayers.BeanStub> result = StubLayers.merge(List.of(List.of(DEFAULTS_ANY), List.of(DEFAULTS_SEVEN)),
                StubLayers.BeanStub::key);

        assertThat(result).containsExactly(DEFAULTS_ANY, DEFAULTS_SEVEN);
    }

    @Test
    void merge_whenParamTypesDiffer_shouldKeepBothStubs() {
        StubLayers.BeanStub typed = new StubLayers.BeanStub("placementAccess", stub(new MethodRef("getPlacement",
                List.of("long")), List.of(new ArgMatcher.Any()), 4), "case");

        List<StubLayers.BeanStub> result = StubLayers.merge(List.of(List.of(DEFAULTS_ANY), List.of(typed)),
                StubLayers.BeanStub::key);

        assertThat(result).containsExactly(DEFAULTS_ANY, typed);
    }

    @Test
    void merge_whenHttpStubsShareServiceMethodAndPath_shouldKeepOnlyTheLaterOne() {
        StubLayers.HttpStubAt defaults = new StubLayers.HttpStubAt(httpStub("GET", "/bid", 200), "defaults");
        StubLayers.HttpStubAt otherPath = new StubLayers.HttpStubAt(httpStub("GET", "/health", 200), "defaults");
        StubLayers.HttpStubAt override = new StubLayers.HttpStubAt(httpStub("GET", "/bid", 503), "case");
        StubLayers.HttpStubAt otherMethod = new StubLayers.HttpStubAt(httpStub("POST", "/bid", 200), "case");

        List<StubLayers.HttpStubAt> result = StubLayers.merge(List.of(List.of(defaults, otherPath),
                List.of(override, otherMethod)), StubLayers.HttpStubAt::key);

        assertThat(result).containsExactly(otherPath, override, otherMethod);
    }

    @Test
    void merge_whenOneLayerHasHttpStubsOnOnePathWithDifferentQuery_shouldKeepBoth() {
        StubLayers.HttpStubAt first = new StubLayers.HttpStubAt(httpStub("GET", "/items", Map.of("id", "1"), 200),
                "case");
        StubLayers.HttpStubAt second = new StubLayers.HttpStubAt(httpStub("GET", "/items", Map.of("id", "2"), 404),
                "case");

        List<StubLayers.HttpStubAt> result = StubLayers.merge(List.of(List.of(first, second)),
                StubLayers.HttpStubAt::key);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void merge_whenOneLayerHasTwoStubsWithTheSameKey_shouldKeepBothInOrder() {
        StubLayers.HttpStubAt first = new StubLayers.HttpStubAt(httpStub("GET", "/bid", 200), "case");
        StubLayers.HttpStubAt second = new StubLayers.HttpStubAt(httpStub("GET", "/bid", 503), "case");

        List<StubLayers.HttpStubAt> result = StubLayers.merge(List.of(List.of(first, second)),
                StubLayers.HttpStubAt::key);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void merge_whenALaterLayerStubsTheSamePathWithAnotherQuery_shouldKeepTheEarlierStub() {
        StubLayers.HttpStubAt defaults = new StubLayers.HttpStubAt(httpStub("GET", "/items", Map.of("id", "1"), 200),
                "defaults");
        StubLayers.HttpStubAt sameQuery = new StubLayers.HttpStubAt(httpStub("GET", "/items", Map.of("id", "1"), 500),
                "defaults");
        StubLayers.HttpStubAt otherQuery = new StubLayers.HttpStubAt(httpStub("GET", "/items", Map.of("id", "2"),
                404), "case");
        StubLayers.HttpStubAt override = new StubLayers.HttpStubAt(httpStub("GET", "/items", Map.of("id", "1"), 503),
                "case");

        List<StubLayers.HttpStubAt> result = StubLayers.merge(List.of(List.of(defaults, sameQuery),
                List.of(otherQuery, override)), StubLayers.HttpStubAt::key);

        assertThat(result).containsExactly(otherQuery, override);
    }

    @Test
    void merge_whenHttpStubsDifferInHeadersOrBody_shouldKeepBoth() {
        StubLayers.HttpStubAt plain = new StubLayers.HttpStubAt(httpStub("POST", "/bid", 200), "defaults");
        StubLayers.HttpStubAt withHeader = new StubLayers.HttpStubAt(new HttpStub("partner", new RequestMatcher("POST",
                "/bid", null, Map.of(), Map.of("X-Mode", "eco"), MissingNode.getInstance()), plain.stub().responses(),
                false), "case");
        StubLayers.HttpStubAt withBody = new StubLayers.HttpStubAt(new HttpStub("partner", new RequestMatcher("POST",
                "/bid", null, Map.of(), Map.of(), TextNode.valueOf("x")), plain.stub().responses(), false), "case");

        List<StubLayers.HttpStubAt> result = StubLayers.merge(List.of(List.of(plain), List.of(withHeader, withBody)),
                StubLayers.HttpStubAt::key);

        assertThat(result).containsExactly(plain, withHeader, withBody);
    }

    @Test
    void beanStubs_whenSetHasMocksAndSpies_shouldListEachStubWithItsLocation() {
        StubSet stubs = new StubSet(List.of(new BeanEntry("placementAccess", List.of(DEFAULT_ANY)),
                new BeanEntry("redis", List.of())), List.of(new BeanEntry("auditService", List.of(CASE_ANY))),
                List.of());

        List<StubLayers.BeanStub> result = StubLayers.beanStubs(stubs, "cases/a.yaml, case 'c'");

        assertThat(result).containsExactly(
                new StubLayers.BeanStub("placementAccess", DEFAULT_ANY, "cases/a.yaml, case 'c', mocks placementAccess"),
                new StubLayers.BeanStub("auditService", CASE_ANY, "cases/a.yaml, case 'c', spies auditService"));
    }

    private static Stub stub(MethodRef method, List<ArgMatcher> args, int returns) {
        return new Stub(method, args, new StubAnswer.Returns(IntNode.valueOf(returns)));
    }

    private static ArgMatcher literal(String value) {
        return new ArgMatcher.Literal(TextNode.valueOf(value));
    }

    private static HttpStub httpStub(String method, String path, int status) {
        return httpStub(method, path, Map.of(), status);
    }

    private static HttpStub httpStub(String method, String path, Map<String, String> query, int status) {
        return new HttpStub("partner", new RequestMatcher(method, path, null, query, Map.of(),
                MissingNode.getInstance()), List.of(new StubResponse(status, Map.of(), MissingNode.getInstance(), 0,
                null, false)), false);
    }
}
