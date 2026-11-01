package com.yamltestkit.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Expectations of one step. Absent single-valued parts are {@code null}; {@code returns} is a {@code MissingNode}
 * when not given, so {@code returns: null} (a {@code NullNode}) stays an expectation of Java {@code null}.
 */
public record Expect(
        @Nullable Integer status,
        Map<String, String> headers,
        @Nullable BodyExpectation body,
        @Nullable TextExpectation bodyText,
        List<Assertion> asserts,
        JsonNode returns,
        @Nullable ExpectedThrow throwsException,
        List<VerifyCheck> verify,
        List<LogExpectation> logs,
        @Nullable ContextExpectation context,
        @Nullable Polling polling
) {

    public static final Expect NONE = new Expect(null, Map.of(), null, null, List.of(), MissingNode.getInstance(), null,
            List.of(), List.of(), null, null);

    public Expect {
        headers = Map.copyOf(headers);
        asserts = List.copyOf(asserts);
        verify = List.copyOf(verify);
        logs = List.copyOf(logs);
    }

    /** True when an expectation that applies only to {@code http} calls is present. */
    public boolean hasHttpOnlyParts() {
        return status != null || !headers.isEmpty() || body != null || bodyText != null;
    }

    /** True when an expectation that applies only to {@code bean} / {@code class} calls is present. */
    public boolean hasReturnParts() {
        return !returns.isMissingNode() || throwsException != null;
    }
}
