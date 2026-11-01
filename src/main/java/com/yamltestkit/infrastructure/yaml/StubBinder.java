package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.BeanEntry;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Dependency;
import com.yamltestkit.domain.model.HttpStub;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.RequestMatcher;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.StubResponse;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.ThrowSpec;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Binds the stub grammar: {@code mocks} / {@code spies} entries, method stubs and their answers, WireMock
 * {@code http} stubs and unit {@code deps}. Shared by case files and, later, {@code _context.yaml} /
 * {@code _defaults.yaml}.
 */
@RequiredArgsConstructor
final class StubBinder {

    private final PayloadResolver payloads;

    /** The {@code mocks}, {@code spies} and {@code http} keys of a case, step, defaults block or defaults file. */
    StubSet stubSet(JsonNode node) {
        return new StubSet(
                Nodes.listField(node, "mocks", this::beanEntry),
                Nodes.listField(node, "spies", this::beanEntry),
                Nodes.listField(node, "http", this::httpStub));
    }

    BeanEntry beanEntry(JsonNode node) {
        if (node.isTextual()) {
            return new BeanEntry(node.asText(), List.of());
        }
        return new BeanEntry(node.get("bean").asText(), List.of(stub(node)));
    }

    Stub stub(JsonNode node) {
        return new Stub(methodRef(node), argMatchers(node), answer(node));
    }

    static MethodRef methodRef(JsonNode node) {
        return new MethodRef(node.get("method").asText(), Nodes.stringListOrNull(node, "paramTypes"));
    }

    @Nullable List<ArgMatcher> argMatchers(JsonNode node) {
        JsonNode args = node.get("args");
        return args == null ? null : Nodes.list(args, this::argMatcher);
    }

    private ArgMatcher argMatcher(JsonNode node) {
        if (node.isTextual() && ArgMatcher.ANY_TOKEN.equals(node.asText())) {
            return new ArgMatcher.Any();
        }
        if (node.isObject() && node.size() == 1 && node.path("anyOf").isTextual()) {
            return new ArgMatcher.OfType(node.get("anyOf").asText());
        }
        return new ArgMatcher.Literal(payloads.json(node));
    }

    private StubAnswer answer(JsonNode node) {
        if (node.has("returnsArg")) {
            return new StubAnswer.ReturnsArg(node.get("returnsArg").asInt());
        }
        if (node.has("answers")) {
            return new StubAnswer.Consecutive(Nodes.list(node.get("answers"), this::answer));
        }
        if (node.has("throws")) {
            return new StubAnswer.Throws(throwSpec(node.get("throws")));
        }
        return new StubAnswer.Returns(payloads.json(node.get("returns")));
    }

    private ThrowSpec throwSpec(JsonNode node) {
        JsonNode cause = node.get("cause");
        return new ThrowSpec(node.get("type").asText(), Nodes.text(node, "message"),
                Nodes.listField(node, "args", payloads::json), cause == null ? null : throwSpec(cause));
    }

    HttpStub httpStub(JsonNode node) {
        boolean sequence = node.has("responses");
        List<StubResponse> responses = sequence
                ? Nodes.list(node.get("responses"), this::response)
                : List.of(response(node.get("response")));
        return new HttpStub(node.get("service").asText(), requestMatcher(node), responses, sequence);
    }

    RequestMatcher requestMatcher(JsonNode node) {
        return new RequestMatcher(Nodes.text(node, "method"), Nodes.text(node, "path"), Nodes.text(node, "pathPattern"),
                Nodes.scalarMap(node, "query"), lowercaseNames(Nodes.scalarMap(node, "headers")),
                payload(node, "body"));
    }

    /**
     * Header names in lowercase: HTTP header names are case-insensitive, so WireMock matching and stub layering must
     * not depend on how a case spells them.
     */
    private static Map<String, String> lowercaseNames(Map<String, String> headers) {
        Map<String, String> lowercase = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (lowercase.put(name.toLowerCase(Locale.ROOT), value) != null) {
                throw new CaseDefinitionException("header '" + name + "' is given twice; header names are"
                        + " case-insensitive");
            }
        });
        return Collections.unmodifiableMap(lowercase);
    }

    private StubResponse response(JsonNode node) {
        Integer status = Nodes.integer(node, "status");
        Integer delayMs = Nodes.integer(node, "delayMs");
        return new StubResponse(status == null ? StubResponse.DEFAULT_STATUS : status, Nodes.scalarMap(node, "headers"),
                payload(node, "body"), delayMs == null ? 0 : delayMs, Nodes.text(node, "fault"),
                Nodes.flag(node, "template"));
    }

    Map<String, Dependency> deps(JsonNode node) {
        Map<String, Dependency> deps = new LinkedHashMap<>();
        node.path("deps").properties().forEach(entry -> deps.put(entry.getKey(), dependency(entry.getValue())));
        return Collections.unmodifiableMap(deps);
    }

    private Dependency dependency(JsonNode node) {
        if (node.has("stubs")) {
            return new Dependency.Stubbed(Nodes.list(node.get("stubs"), this::stub));
        }
        if (node.has("value")) {
            return new Dependency.Value(payloads.json(node.get("value")));
        }
        return new Dependency.Real();
    }

    /** A payload field: its resolved value, or a {@code MissingNode} when absent. */
    JsonNode payload(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? MissingNode.getInstance() : payloads.json(value);
    }
}
