package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.domain.assertion.ExpectedValueResolver;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.YamlCase;
import com.yamltestkit.infrastructure.mockito.StubErrors;
import com.yamltestkit.infrastructure.spring.CaseContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * State of one case while its steps run, shared by the step executors: the application context and WireMock servers
 * of an {@code http} / {@code bean} case, the dependency mocks of each class step's instance (the targets of
 * {@code verify} on {@code dep}), the errors its stubs raised, and later step results and verification captures for
 * references.
 */
@RequiredArgsConstructor
public final class CaseRun implements ExpectedValueResolver {

    @Getter
    @Accessors(fluent = true)
    private final YamlCase yamlCase;
    @Getter
    @Accessors(fluent = true)
    private final StubErrors stubErrors = new StubErrors();
    private final Map<String, Map<String, Object>> unitMocksByStep = new HashMap<>();
    private final References references = new References();
    private @Nullable CaseContext caseContext;

    /** How a step is named in failure messages: case file, case and step. */
    public String where(Step step) {
        return yamlCase.where() + ", step '" + step.name() + "'";
    }

    /** Sets the context the case's {@code http} / {@code bean} steps run against. */
    public void attach(CaseContext context) {
        this.caseContext = context;
    }

    /**
     * @throws IllegalStateException when no context was attached, i.e. the case has no {@code http} / {@code bean}
     *                               call
     */
    public CaseContext caseContext() {
        if (caseContext == null) {
            throw new IllegalStateException(yamlCase.where() + ": no application context was started for this case");
        }
        return caseContext;
    }

    /**
     * Before a step is retried ({@code within}): forgets the calls recorded on the context's mocks and spies and the
     * WireMock journals, so count checks apply to the attempt. A class step builds new dependency mocks per attempt.
     */
    public void clearRecordings() {
        if (caseContext != null) {
            caseContext.clearRecordings();
        }
    }

    /** Keeps the dependency mocks a class step's instance was built with, by {@code deps} key or parameter name. */
    public void rememberUnitMocks(Step step, Map<String, Object> mocks) {
        unitMocksByStep.put(step.name(), Map.copyOf(mocks));
    }

    /** The dependency mocks of the named class step's instance; empty for a step that built none. */
    public Map<String, Object> unitMocks(String stepName) {
        return unitMocksByStep.getOrDefault(stepName, Map.of());
    }

    /**
     * Keeps the step's result for references to the step's name: the response body of an {@code http} call, the
     * returned value's JSON of a {@code bean} / {@code class} call.
     *
     * @param result computed only when a reference reads it; a {@link RuntimeException} it throws (e.g. the call
     *               threw) says why the step has no result
     */
    public void recordResult(Step step, Supplier<JsonNode> result) {
        references.put(step.name(), result);
    }

    /** Keeps the captured calls of a {@code verify} check under its {@code as} name. */
    public void recordCapture(String name, JsonNode capturedCalls) {
        references.put(name, () -> capturedCalls);
    }

    /**
     * The value itself, or for a {@code { ref }} the recorded step result or capture it points to.
     *
     * @throws org.opentest4j.AssertionFailedError when the reference can't be read
     */
    @Override
    public JsonNode resolve(JsonNode expected) {
        return references.resolve(expected, yamlCase.where());
    }
}
