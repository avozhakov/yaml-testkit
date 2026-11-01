package com.yamltestkit.convert.protobuf;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Method;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.yamltestkit.convert.ConversionException;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The protobuf converter as registered in the chain by class name; {@code Struct} and {@code Method} are real
 * messages shipped with {@code protobuf-java}.
 */
class ProtobufConverterTest {

    private static final String LOCATION = "cases/a.yaml, case 'c', step 'main': returns";
    private static final Struct PLACEMENT = Struct.newBuilder()
            .putFields("id", Value.newBuilder().setNumberValue(13038).build())
            .putFields("name", Value.newBuilder().setStringValue("top").build())
            .build();

    private ValueConverters underTest;

    @BeforeEach
    void setUp() {
        underTest = ValueConverters.create(List.of());
    }

    @Test
    void fromYaml_whenTargetIsAMessage_shouldParseTheProto3JsonForm() {
        Object result = underTest.fromYaml(json("{\"id\": 13038, \"name\": \"top\"}"), Struct.class, LOCATION);

        assertThat(result).isEqualTo(PLACEMENT);
    }

    @Test
    void fromYaml_whenMessageIsNestedInAnOptional_shouldConvertItThroughTheChain() {
        Object result = underTest.fromYaml(json("{\"id\": 13038, \"name\": \"top\"}"),
                GenericTypes.parameterized(Optional.class, Struct.class), LOCATION);

        assertThat(result).isEqualTo(Optional.of(PLACEMENT));
    }

    @Test
    void fromYaml_whenYamlHasAnUnknownField_shouldFailNamingTheConverter() {
        assertThatThrownBy(() -> underTest.fromYaml(json("{\"name\": \"fetch\", \"nope\": 1}"), Method.class,
                LOCATION))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining(Method.class.getName())
                .hasMessageContaining(LOCATION)
                .hasMessageContaining("ProtobufConverter failed")
                .hasMessageContaining("nope");
    }

    @Test
    void toJson_whenMessageHasDefaultValuedFields_shouldWriteThemToo() {
        JsonNode result = underTest.toJson(Method.newBuilder().setName("fetch").build());

        assertThat(result.get("name").asText()).isEqualTo("fetch");
        assertThat(result.get("requestStreaming").asBoolean(true)).isFalse();
        assertThat(result.get("requestTypeUrl").asText()).isEmpty();
    }

    @Test
    void toJson_whenMessageIsAStruct_shouldWriteAPlainJsonObject() {
        JsonNode result = underTest.toJson(PLACEMENT);

        assertThat(result).isEqualTo(json("{\"id\": 13038.0, \"name\": \"top\"}"));
    }
}
