package com.yamltestkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.convert.ConversionContext;
import com.yamltestkit.convert.ValueConverter;
import com.yamltestkit.domain.model.CallKind;
import com.yamltestkit.engine.SuiteSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlCasesReaderTest {

    private static final String FILTER = "discover/**";

    @AfterEach
    void tearDown() {
        System.clearProperty(SuiteSettings.FILTER_PROPERTY);
    }

    @Test
    void read_whenSuiteIsAnnotated_shouldReadGlobsKindsConvertersAndFilter() {
        System.setProperty(SuiteSettings.FILTER_PROPERTY, FILTER);

        SuiteSettings result = YamlCasesReader.read(AnnotatedSuite.class);

        assertThat(result).isEqualTo(new SuiteSettings(List.of("cases/**", "more/**"),
                Set.of(CallKind.HTTP, CallKind.BEAN), List.of(MoneyConverter.class), FILTER));
    }

    @Test
    void read_whenKindsAreOmitted_shouldSelectEveryKind() {
        SuiteSettings result = YamlCasesReader.read(DefaultKindsSuite.class);

        assertThat(result.kinds()).containsExactlyInAnyOrder(CallKind.values());
        assertThat(result.filter()).isNull();
    }

    @Test
    void read_whenSuiteHasNoAnnotation_shouldFailNamingTheClass() {
        assertThatThrownBy(() -> YamlCasesReader.read(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("java.lang.String")
                .hasMessageContaining("@YamlCases");
    }

    @YamlCases(value = {"cases/**", "more/**"}, kinds = {CallKind.HTTP, CallKind.BEAN},
            converters = MoneyConverter.class)
    private static final class AnnotatedSuite {
    }

    @YamlCases("cases/**")
    private static final class DefaultKindsSuite {
    }

    private static final class MoneyConverter implements ValueConverter {

        @Override
        public boolean supports(Type type) {
            return false;
        }

        @Override
        public Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
            throw new UnsupportedOperationException("never selected");
        }

        @Override
        public JsonNode toJson(Object value, ConversionContext ctx) {
            throw new UnsupportedOperationException("never selected");
        }
    }
}
