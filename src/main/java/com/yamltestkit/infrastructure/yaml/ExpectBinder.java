package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.model.AssertOperator;
import com.yamltestkit.domain.model.Assertion;
import com.yamltestkit.domain.model.BodyExpectation;
import com.yamltestkit.domain.model.BodyMode;
import com.yamltestkit.domain.model.CallCount;
import com.yamltestkit.domain.model.ContextExpectation;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.ExpectedThrow;
import com.yamltestkit.domain.model.LogExpectation;
import com.yamltestkit.domain.model.Polling;
import com.yamltestkit.domain.model.TextExpectation;
import com.yamltestkit.domain.model.TextOperator;
import com.yamltestkit.domain.model.ValueRef;
import com.yamltestkit.domain.model.VerifyCheck;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Binds {@code expect}: response and return expectations, {@code assert} entries, {@code verify} checks,
 * {@code logs}, {@code context} and polling.
 */
@RequiredArgsConstructor
final class ExpectBinder {

    /** Operators whose value is an expected value: it may be a {@code { file }} payload or a {@code { ref }}. */
    private static final Set<AssertOperator> EXPECTED_VALUE_OPERATORS =
            Set.of(AssertOperator.EQUALS, AssertOperator.NOT_EQUALS, AssertOperator.CONTAINS);

    private final PayloadResolver payloads;
    private final StubBinder stubs;

    Expect expect(@Nullable JsonNode node) {
        if (node == null) {
            return Expect.NONE;
        }
        return new Expect(Nodes.integer(node, "status"), Nodes.scalarMap(node, "headers"), body(node.get("body")),
                bodyText(node.get("bodyText")), assertions(node, "assert"), expectedValue(node, "returns"),
                expectedThrow(node.get("throws")), Nodes.listField(node, "verify", this::verifyCheck),
                Nodes.listField(node, "logs", ExpectBinder::logExpectation), context(node.get("context")),
                polling(node));
    }

    private @Nullable BodyExpectation body(@Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        boolean withOptions = node.isObject() && (node.has("mode") || node.has("ignore"))
                && (node.has("file") || node.has("value"));
        if (!withOptions) {
            return new BodyExpectation(expectedValue(node), BodyMode.LENIENT, List.of());
        }
        BodyMode mode = node.has("mode") ? BodyMode.fromYaml(node.get("mode").asText()) : BodyMode.LENIENT;
        JsonNode expected = node.has("file") ? fileBody(node.get("file").asText(), mode) : node.get("value");
        return new BodyExpectation(expected, mode, Nodes.stringList(node, "ignore"));
    }

    private JsonNode fileBody(String path, BodyMode mode) {
        return mode == BodyMode.EXACT ? TextNode.valueOf(payloads.readText(path)) : payloads.jsonFile(path);
    }

    private @Nullable TextExpectation bodyText(@Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        Map<TextOperator, List<String>> matchers = new EnumMap<>(TextOperator.class);
        for (TextOperator operator : TextOperator.values()) {
            JsonNode value = node.get(operator.yamlName());
            if (value != null) {
                matchers.put(operator, value.isArray() ? Nodes.list(value, this::textValue) : List.of(textValue(value)));
            }
        }
        return new TextExpectation(matchers);
    }

    private String textValue(JsonNode node) {
        return payloads.text(node).asText();
    }

    List<Assertion> assertions(JsonNode node, String field) {
        return Nodes.listField(node, field, this::assertion);
    }

    private Assertion assertion(JsonNode node) {
        Map<AssertOperator, JsonNode> operators = new EnumMap<>(AssertOperator.class);
        for (AssertOperator operator : AssertOperator.values()) {
            JsonNode value = node.get(operator.yamlName());
            if (value != null) {
                operators.put(operator, EXPECTED_VALUE_OPERATORS.contains(operator) ? expectedValue(value) : value);
            }
        }
        JsonNode mode = node.get("mode");
        JsonNode all = node.get("all");
        JsonNode none = node.get("none");
        return new Assertion(node.get("path").asText(), Nodes.flag(node, "parseJson"),
                mode == null ? null : BodyMode.fromYaml(mode.asText()), operators,
                all == null ? null : assertion(all), none == null ? null : assertion(none));
    }

    private JsonNode expectedValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? MissingNode.getInstance() : expectedValue(value);
    }

    private JsonNode expectedValue(JsonNode node) {
        return ValueRef.isRef(node) ? node : payloads.json(node);
    }

    private static @Nullable ExpectedThrow expectedThrow(@Nullable JsonNode node) {
        return node == null ? null : new ExpectedThrow(node.get("type").asText(), Nodes.text(node, "message"));
    }

    private VerifyCheck verifyCheck(JsonNode node) {
        CallCount count = callCount(node);
        Duration within = Nodes.duration(node, "within");
        String as = Nodes.text(node, "as");
        List<Assertion> asserts = assertions(node, "assert");
        if (node.has("service")) {
            return new VerifyCheck.OnService(node.get("service").asText(), stubs.requestMatcher(node), count, within,
                    as, asserts);
        }
        boolean onBean = node.has("bean");
        JsonNode argType = node.get("argType");
        return new VerifyCheck.OnMock(
                onBean ? VerifyCheck.MockTarget.BEAN : VerifyCheck.MockTarget.DEP,
                node.get(onBean ? "bean" : "dep").asText(),
                node.has("method") ? StubBinder.methodRef(node) : null,
                stubs.argMatchers(node),
                argType == null ? null : new VerifyCheck.ArgTypeFilter(argType.get("index").asInt(),
                        argType.get("type").asText()),
                count, within, as, asserts);
    }

    private static CallCount callCount(JsonNode node) {
        if (Nodes.flag(node, "noInteractions")) {
            return new CallCount.NoInteractions();
        }
        if (Nodes.flag(node, "never")) {
            return new CallCount.Times(0);
        }
        if (node.has("times")) {
            return new CallCount.Times(node.get("times").asInt());
        }
        if (node.has("atLeast")) {
            return new CallCount.AtLeast(node.get("atLeast").asInt());
        }
        return CallCount.AT_LEAST_ONCE;
    }

    private static LogExpectation logExpectation(JsonNode node) {
        return new LogExpectation(node.get("logger").asText(),
                LogExpectation.LogLevel.valueOf(node.get("level").asText()), Nodes.text(node, "contains"),
                Nodes.integer(node, "times"), Nodes.flag(node, "absent"));
    }

    private static @Nullable ContextExpectation context(@Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        return new ContextExpectation(presence(node.get("beans")), presence(node.get("beanTypes")));
    }

    private static ContextExpectation.Presence presence(@Nullable JsonNode node) {
        return node == null
                ? ContextExpectation.Presence.NONE
                : new ContextExpectation.Presence(Nodes.stringList(node, "present"), Nodes.stringList(node, "absent"));
    }

    private static @Nullable Polling polling(JsonNode node) {
        Duration within = Nodes.duration(node, "within");
        if (within == null) {
            return null;
        }
        Duration every = Nodes.duration(node, "every");
        return new Polling(within, every == null ? Polling.DEFAULT_EVERY : every);
    }
}
