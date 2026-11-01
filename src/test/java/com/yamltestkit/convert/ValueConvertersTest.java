package com.yamltestkit.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import lombok.Builder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueConvertersTest {

    private static final String LOCATION = "cases/a.yaml, case 'c', step 'main': args[0]";
    private static final Type OPTIONAL_PROVIDER = GenericTypes.parameterized(Optional.class, Provider.class);
    private static final Type FUTURE_PROVIDER = GenericTypes.parameterized(CompletableFuture.class, Provider.class);
    private static final Type MAP_OF_STRINGS = GenericTypes.parameterized(Map.class, String.class, String.class);
    private static final Provider AMAZON = new Provider("amazon", 4);

    private ValueConverters underTest;

    @BeforeEach
    void setUp() {
        underTest = ValueConverters.create(List.of());
    }

    @Test
    void fromYaml_whenTargetIsARecord_shouldUseItsCanonicalConstructor() {
        Object result = underTest.fromYaml(json("{\"name\": \"amazon\", \"maxAds\": 4}"), Provider.class, LOCATION);

        assertThat(result).isEqualTo(AMAZON);
    }

    @Test
    void fromYaml_whenTargetIsAMapWithANullValue_shouldKeepTheKeyWithNull() {
        Object result = underTest.fromYaml(json("{\"qt\": null}"), MAP_OF_STRINGS, LOCATION);

        Map<String, String> expected = new HashMap<>();
        expected.put("qt", null);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void fromYaml_whenValueIsNull_shouldGiveJavaNull() {
        Object result = underTest.fromYaml(NullNode.getInstance(), Provider.class, LOCATION);

        assertThat(result).isNull();
    }

    @Test
    void fromYaml_whenNullTargetsAPrimitive_shouldFailNamingTheType() {
        assertThatThrownBy(() -> underTest.fromYaml(NullNode.getInstance(), int.class, LOCATION))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("primitive int");
    }

    @Test
    void fromYaml_whenTargetIsOptional_shouldWrapTheValueAndMapNullToEmpty() {
        Object present = underTest.fromYaml(json("{\"name\": \"amazon\", \"maxAds\": 4}"), OPTIONAL_PROVIDER,
                LOCATION);
        Object empty = underTest.fromYaml(NullNode.getInstance(), OPTIONAL_PROVIDER, LOCATION);

        assertThat(present).isEqualTo(Optional.of(AMAZON));
        assertThat(empty).isEqualTo(Optional.empty());
    }

    @Test
    void fromYaml_whenTargetIsCompletableFuture_shouldGiveACompletedFutureOfTheConvertedValue() {
        Object result = underTest.fromYaml(json("{\"name\": \"amazon\", \"maxAds\": 4}"), FUTURE_PROVIDER, LOCATION);

        assertThat(result).isInstanceOf(CompletableFuture.class);
        assertThat(((CompletableFuture<?>) result).join()).isEqualTo(AMAZON);
    }

    @Test
    void fromYaml_whenTargetHasALombokBuilder_shouldBuildIt() {
        Object result = underTest.fromYaml(json("{\"maxAds\": 4, \"sections\": [\"top\"]}"), LombokModel.class,
                LOCATION);

        assertThat(result).isInstanceOf(LombokModel.class);
        assertThat(((LombokModel) result).getMaxAds()).isEqualTo(4);
        assertThat(((LombokModel) result).getSections()).containsExactly("top");
    }

    @Test
    void fromYaml_whenTargetHasASetterStyleBuilder_shouldCallSetXForEachField() {
        Object result = underTest.fromYaml(json("{\"maxAds\": 4}"), SetterModel.class, LOCATION);

        assertThat(((SetterModel) result).maxAds()).isEqualTo(4);
    }

    @Test
    void fromYaml_whenRecordNestsAnOptionalAndAList_shouldConvertNestedValuesThroughTheChain() {
        Object result = underTest.fromYaml(json("{\"main\": null, \"models\": [{\"maxAds\": 2}]}"), Holder.class,
                LOCATION);

        assertThat(((Holder) result).main()).isEmpty();
        assertThat(((Holder) result).models()).singleElement().extracting(SetterModel::maxAds).isEqualTo(2);
    }

    @Test
    void fromYaml_whenARecordGetsAnUnknownField_shouldNameTypeLocationAndConvertersTried() {
        assertThatThrownBy(() -> underTest.fromYaml(json("{\"name\": \"amazon\", \"maxAds\": 4, \"extra\": 1}"),
                Provider.class, LOCATION))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining(Provider.class.getName())
                .hasMessageContaining(LOCATION)
                .hasMessageContaining("Unrecognized field \"extra\"")
                .hasMessageContaining("converters tried: ")
                .hasMessageContaining("OptionalConverter → BuilderConverter → JacksonConverter");
    }

    @Test
    void fromYaml_whenUserConverterSupportsTheType_shouldUseItBeforeTheBuiltIns() {
        ValueConverters withUserConverter = ValueConverters.create(List.of(UpperCaseConverter.class));

        Object result = withUserConverter.fromYaml(TextNode.valueOf("shoes"), String.class, LOCATION);

        assertThat(result).isEqualTo("SHOES");
    }

    @Test
    void create_whenUserConverterHasNoNoArgumentConstructor_shouldFailNamingIt() {
        assertThatThrownBy(() -> ValueConverters.create(List.of(NoDefaultConstructorConverter.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NoDefaultConstructorConverter.class.getName())
                .hasMessageContaining("no-argument constructor");
    }

    @Test
    void toJson_whenValueIsABuilderObjectWithoutGetters_shouldWriteItsFields() {
        JsonNode result = underTest.toJson(SetterModel.builder().setMaxAds(4).build());

        assertThat(result).isEqualTo(json("{\"maxAds\": 4}"));
    }

    @Test
    void toJson_whenValueHoldsOptionalsAndTime_shouldWriteContentNullAndIsoText() {
        JsonNode result = underTest.toJson(new Timed(Optional.empty(), Optional.of(AMAZON),
                Instant.parse("2025-01-01T00:00:00Z")));

        assertThat(result).isEqualTo(json(
                "{\"none\": null, \"some\": {\"name\": \"amazon\", \"maxAds\": 4}, \"at\": \"2025-01-01T00:00:00Z\"}"));
    }

    @Test
    void toJson_whenValueIsACompletedFuture_shouldWriteItsResult() {
        JsonNode result = underTest.toJson(CompletableFuture.completedFuture(AMAZON));

        assertThat(result).isEqualTo(json("{\"name\": \"amazon\", \"maxAds\": 4}"));
    }

    @Test
    void wrap_whenTargetIsAWrapperType_shouldWrapTheSameInstance() {
        Object optional = underTest.wrap(AMAZON, OPTIONAL_PROVIDER, LOCATION);
        Object future = underTest.wrap(AMAZON, FUTURE_PROVIDER, LOCATION);

        assertThat(((Optional<?>) optional).orElseThrow()).isSameAs(AMAZON);
        assertThat(((CompletableFuture<?>) future).join()).isSameAs(AMAZON);
    }

    @Test
    void wrap_whenNoConverterWrapsTheType_shouldFailNamingTheConvertersTried() {
        assertThatThrownBy(() -> underTest.wrap(AMAZON, String.class, LOCATION))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("no converter can wrap")
                .hasMessageContaining(LOCATION)
                .hasMessageContaining("JacksonConverter");
    }

    @Test
    void wrappedType_whenTargetIsAWrapperType_shouldGiveItsContentType() {
        Optional<Type> optional = underTest.wrappedType(OPTIONAL_PROVIDER);
        Optional<Type> future = underTest.wrappedType(FUTURE_PROVIDER);
        Optional<Type> plain = underTest.wrappedType(Provider.class);

        assertThat(optional).contains(Provider.class);
        assertThat(future).contains(Provider.class);
        assertThat(plain).isEmpty();
    }

    @Test
    void fromYaml_whenAFractionTargetsAnInteger_shouldFailInsteadOfTruncating() {
        assertThatThrownBy(() -> underTest.fromYaml(json("{\"name\": \"amazon\", \"maxAds\": 4.5}"), Provider.class,
                LOCATION))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining(LOCATION);
    }

    @Test
    void fromYaml_whenNullTargetsAPrimitiveField_shouldFailInsteadOfDefaulting() {
        assertThatThrownBy(() -> underTest.fromYaml(json("{\"name\": \"amazon\", \"maxAds\": null}"),
                Provider.class, LOCATION))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining(LOCATION);
    }

    @Test
    void fromYaml_whenARecordMissesAPrimitiveComponent_shouldFailInsteadOfDefaulting() {
        assertThatThrownBy(() -> underTest.fromYaml(json("{\"name\": \"amazon\"}"), Provider.class, LOCATION))
                .isInstanceOf(ConversionException.class)
                .hasMessageContaining("Cannot map `null` into type `int`");
    }

    record Provider(String name, int maxAds) {
    }

    record Holder(Optional<Provider> main, List<SetterModel> models) {
    }

    record Timed(Optional<Provider> none, Optional<Provider> some, Instant at) {
    }

    @Getter
    @Builder
    static final class LombokModel {

        private final int maxAds;
        private final List<String> sections;
    }

    /** A hand-written {@code builder().setX()} class; the builder style is the point, so no Lombok. */
    static final class SetterModel {

        private final int maxAds;

        private SetterModel(int maxAds) {
            this.maxAds = maxAds;
        }

        static Builder builder() {
            return new Builder();
        }

        int maxAds() {
            return maxAds;
        }

        static final class Builder {

            private int maxAds;

            public Builder setMaxAds(int maxAds) {
                this.maxAds = maxAds;
                return this;
            }

            public SetterModel build() {
                return new SetterModel(maxAds);
            }
        }
    }

    public static final class UpperCaseConverter implements ValueConverter {

        @Override
        public boolean supports(Type type) {
            return type == String.class;
        }

        @Override
        public Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
            return value.asText().toUpperCase(Locale.ROOT);
        }

        @Override
        public JsonNode toJson(Object value, ConversionContext ctx) {
            return TextNode.valueOf(value.toString());
        }
    }

    public static final class NoDefaultConstructorConverter implements ValueConverter {

        private final String name;

        public NoDefaultConstructorConverter(String name) {
            this.name = name;
        }

        @Override
        public boolean supports(Type type) {
            return false;
        }

        @Override
        public Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
            throw new UnsupportedOperationException(name);
        }

        @Override
        public JsonNode toJson(Object value, ConversionContext ctx) {
            throw new UnsupportedOperationException(name);
        }
    }
}
