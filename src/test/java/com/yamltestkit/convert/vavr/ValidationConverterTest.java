package com.yamltestkit.convert.vavr;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.convert.ConversionException;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import io.vavr.control.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The vavr {@code Validation} converter as registered in the chain by class name.
 */
class ValidationConverterTest {

    private static final String LOCATION = "cases/a.yaml, case 'c', step 'main': returns";
    private static final Type VALIDATION = GenericTypes.parameterized(Validation.class, String.class, Context.class);
    private static final Context CONTEXT = new Context("req-1", 4);

    private ValueConverters underTest;

    @BeforeEach
    void setUp() {
        underTest = ValueConverters.create(List.of());
    }

    @Test
    void fromYaml_whenValueIsValid_shouldConvertTheContentToTheValidType() {
        Object result = underTest.fromYaml(json("{\"valid\": {\"requestId\": \"req-1\", \"maxAds\": 4}}"),
                VALIDATION, LOCATION);

        assertThat(result).isEqualTo(Validation.valid(CONTEXT));
    }

    @Test
    void fromYaml_whenValueIsInvalid_shouldConvertTheErrorType() {
        Object result = underTest.fromYaml(json("{\"invalid\": \"no placement\"}"), VALIDATION, LOCATION);

        assertThat(result).isEqualTo(Validation.invalid("no placement"));
    }

    @Test
    void fromYaml_whenValueHasNeitherKey_shouldFailExplainingTheShape() {
        assertThatThrownBy(() -> underTest.fromYaml(json("{\"requestId\": \"req-1\"}"), VALIDATION, LOCATION))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("{ valid: … } or { invalid: … }")
                .hasMessageContaining("ValidationConverter failed");
    }

    @Test
    void toJson_whenValidationIsValidOrInvalid_shouldWriteTheMatchingKey() {
        JsonNode valid = underTest.toJson(Validation.valid(CONTEXT));
        JsonNode invalid = underTest.toJson(Validation.invalid("no placement"));

        assertThat(valid).isEqualTo(json("{\"valid\": {\"requestId\": \"req-1\", \"maxAds\": 4}}"));
        assertThat(invalid).isEqualTo(json("{\"invalid\": \"no placement\"}"));
    }

    @Test
    void wrap_whenTargetIsValidation_shouldWrapTheSameInstanceAsValid() {
        Object result = underTest.wrap(CONTEXT, VALIDATION, LOCATION);

        assertThat(((Validation<?, ?>) result).get()).isSameAs(CONTEXT);
    }

    record Context(String requestId, int maxAds) {
    }
}
