package com.yamltestkit.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.concurrent.atomic.AtomicInteger;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferencesTest {

    private static final String WHERE = "cases/a.yaml, case 'c'";
    private static final JsonNode RESPONSE = json("{\"ads\":[{\"id\":1},{\"id\":2}],\"logged\":\"{\\\"ads\\\":[1]}\"}");

    private References underTest;

    @BeforeEach
    void setUp() {
        underTest = new References();
    }

    @Test
    void resolve_whenValueIsNotAReference_shouldReturnItAsWritten() {
        JsonNode result = underTest.resolve(TextNode.valueOf("req-1"), WHERE);

        assertThat(result).isEqualTo(TextNode.valueOf("req-1"));
    }

    @Test
    void resolve_whenAnObjectHasMoreKeysThanAReference_shouldReturnItAsWritten() {
        ObjectNode value = ref("isp").put("other", 1);

        JsonNode result = underTest.resolve(value, WHERE);

        assertThat(result).isSameAs(value);
    }

    @Test
    void resolve_whenReferenceHasNoPath_shouldReturnTheWholeValue() {
        underTest.put("isp", () -> RESPONSE);

        JsonNode result = underTest.resolve(ref("isp"), WHERE);

        assertThat(result).isEqualTo(RESPONSE);
    }

    @Test
    void resolve_whenReferenceHasAPath_shouldReturnTheSelectedValue() {
        underTest.put("isp", () -> RESPONSE);

        JsonNode result = underTest.resolve(ref("isp").put("path", "$.ads.length()"), WHERE);

        assertThat(result).isEqualTo(IntNode.valueOf(2));
    }

    @Test
    void resolve_whenReferenceParsesJson_shouldReturnTheParsedString() {
        underTest.put("logged", () -> RESPONSE);

        JsonNode result = underTest.resolve(ref("logged").put("path", "$.logged").put("parseJson", true), WHERE);

        assertThat(result).isEqualTo(json("{\"ads\":[1]}"));
    }

    @Test
    void resolve_whenTheValueIsReadTwice_shouldComputeItOnce() {
        AtomicInteger computed = new AtomicInteger();
        underTest.put("isp", () -> IntNode.valueOf(computed.incrementAndGet()));

        underTest.resolve(ref("isp"), WHERE);
        JsonNode result = underTest.resolve(ref("isp"), WHERE);

        assertThat(result).isEqualTo(IntNode.valueOf(1));
    }

    @Test
    void resolve_whenAValueIsRecordedAgain_shouldReturnTheLatest() {
        underTest.put("isp", () -> IntNode.valueOf(1));
        underTest.put("isp", () -> IntNode.valueOf(2));

        JsonNode result = underTest.resolve(ref("isp"), WHERE);

        assertThat(result).isEqualTo(IntNode.valueOf(2));
    }

    @Test
    void resolve_whenThePathIsMissing_shouldFailNamingTheReferenceAndPath() {
        underTest.put("isp", () -> RESPONSE);

        assertThatThrownBy(() -> underTest.resolve(ref("isp").put("path", "$.addz"), WHERE))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessageStartingWith(WHERE + ": reference 'isp': path `$.addz`: the key is missing in {\"ads\":");
    }

    @Test
    void resolve_whenParseJsonGetsNoJson_shouldFailNamingTheReference() {
        underTest.put("isp", () -> TextNode.valueOf("not json"));

        assertThatThrownBy(() -> underTest.resolve(ref("isp").put("parseJson", true), WHERE))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessageStartingWith(WHERE + ": reference 'isp': parseJson: not valid JSON: not json (");
    }

    @Test
    void resolve_whenTheValueCantBeProduced_shouldFailWithTheReason() {
        IllegalStateException threw = new IllegalStateException("the call threw java.io.IOException: down");
        underTest.put("isp", () -> {
            throw threw;
        });

        assertThatThrownBy(() -> underTest.resolve(ref("isp"), WHERE))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessage(WHERE + ": reference 'isp': no value: the call threw java.io.IOException: down")
                .hasCause(threw);
    }

    @Test
    void resolve_whenNothingWasRecorded_shouldFailNamingTheReference() {
        assertThatThrownBy(() -> underTest.resolve(ref("dsp"), WHERE))
                .isInstanceOf(AssertionFailedError.class)
                .hasMessage(WHERE + ": reference 'dsp': nothing was recorded under this name");
    }

    private static ObjectNode ref(String name) {
        return JsonNodeFactory.instance.objectNode().put("ref", name);
    }
}
