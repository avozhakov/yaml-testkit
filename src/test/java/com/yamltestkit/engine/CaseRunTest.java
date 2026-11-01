package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.model.Call;
import com.yamltestkit.domain.model.CaseSource;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.StubSet;
import com.yamltestkit.domain.model.YamlCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseRunTest {

    private static final CaseSource SOURCE = new CaseSource(URI.create("classpath:/cases/unit/a.yaml"), "cases/",
            "unit/a.yaml");
    private static final Step STEP = new Step("convert", new Call.Unit("com.example.Unit", new MethodRef("run", null),
            List.of(), null, Map.of()), StubSet.EMPTY, Expect.NONE);
    private static final JsonNode REFERENCE = JsonNodeFactory.instance.objectNode().put("ref", "isp");

    private CaseRun underTest;

    @BeforeEach
    void setUp() {
        underTest = new CaseRun(new YamlCase("formats titles", SOURCE, List.of(STEP), StubSet.EMPTY, StubSet.EMPTY,
                null, false));
    }

    @Test
    void where_whenStepIsGiven_shouldNameFileCaseAndStep() {
        String result = underTest.where(STEP);

        assertThat(result).isEqualTo("cases/unit/a.yaml, case 'formats titles', step 'convert'");
    }

    @Test
    void unitMocks_whenAStepRememberedItsMocks_shouldGiveThemForThatStepOnly() {
        Object mockClock = new Object();

        underTest.rememberUnitMocks(STEP, Map.of("clock", mockClock));

        assertThat(underTest.unitMocks("convert")).containsExactly(Map.entry("clock", mockClock));
        assertThat(underTest.unitMocks("other")).isEmpty();
    }

    @Test
    void resolve_whenValueIsNotAReference_shouldReturnItAsWritten() {
        JsonNode result = underTest.resolve(TextNode.valueOf("req-1"));

        assertThat(result).isEqualTo(TextNode.valueOf("req-1"));
    }

    @Test
    void resolve_whenAStepResultWasRecorded_shouldReturnIt() {
        underTest.recordResult(new Step("isp", STEP.call(), StubSet.EMPTY, Expect.NONE), () -> TextNode.valueOf("ok"));

        JsonNode result = underTest.resolve(REFERENCE);

        assertThat(result).isEqualTo(TextNode.valueOf("ok"));
    }

    @Test
    void resolve_whenACaptureWasRecorded_shouldReturnIt() {
        underTest.recordCapture("isp", TextNode.valueOf("captured"));

        JsonNode result = underTest.resolve(REFERENCE);

        assertThat(result).isEqualTo(TextNode.valueOf("captured"));
    }

    @Test
    void resolve_whenNothingWasRecorded_shouldFailNamingTheCase() {
        assertThatThrownBy(() -> underTest.resolve(REFERENCE))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessage("cases/unit/a.yaml, case 'formats titles': reference 'isp': nothing was recorded under"
                        + " this name");
    }
}
