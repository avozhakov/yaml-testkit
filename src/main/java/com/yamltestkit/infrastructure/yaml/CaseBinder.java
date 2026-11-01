package com.yamltestkit.infrastructure.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.YamlCase;

import java.util.List;

/**
 * Binds one case (its file defaults already merged) to the model. Checks what the schema can't check before the
 * merge: exactly one of {@code call} / {@code steps}; each {@code call} is checked by {@link CallBinder}.
 */
final class CaseBinder {

    private final CaseSource source;
    private final StubBinder stubs;
    private final ExpectBinder expectations;
    private final CallBinder calls;

    CaseBinder(CaseSource source, PayloadResolver payloads) {
        this.source = source;
        this.stubs = new StubBinder(payloads);
        this.expectations = new ExpectBinder(payloads, stubs);
        this.calls = new CallBinder(stubs, payloads);
    }

    /**
     * @param merged   the case with the file's defaults merged in (stub keys excluded)
     * @param defaults the file's {@code defaults} block, or a missing node
     * @param own      the case as written, whose stub keys form the case stub layer
     */
    YamlCase bind(JsonNode merged, JsonNode defaults, JsonNode own) {
        String name = merged.get("name").asText();
        boolean hasCall = merged.has("call");
        boolean hasSteps = merged.has("steps");
        if (hasCall == hasSteps) {
            throw new CaseDefinitionException(hasCall
                    ? "has both `call` and `steps` (one of them may come from the file's defaults)"
                    : "has neither `call` nor `steps`");
        }
        if (hasSteps && merged.has("expect")) {
            throw new CaseDefinitionException("has `steps` and a case-level `expect`; put `expect` into the steps"
                    + " (it may come from the file's defaults)");
        }
        List<Step> steps = hasCall
                ? List.of(new Step(Step.IMPLICIT_NAME,
                        calls.bind(merged.get("call"), Step.IMPLICIT_NAME, defaults.has("call")), StubSet.EMPTY,
                        expectations.expect(merged.get("expect"))))
                : Nodes.list(merged.get("steps"), this::step);
        StubSet fileDefaults = defaults.isMissingNode() ? StubSet.EMPTY : stubs.stubSet(defaults);
        return new YamlCase(name, source, steps, fileDefaults, stubs.stubSet(own), Nodes.text(merged, "context"),
                Nodes.flag(merged, "freshContext"));
    }

    private Step step(JsonNode node) {
        String name = node.get("name").asText();
        return new Step(name, calls.bind(node.get("call"), name, false), stubs.stubSet(node),
                expectations.expect(node.get("expect")));
    }
}
