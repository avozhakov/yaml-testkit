package com.yamltestkit.infrastructure.mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Dependency;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnitInstanceFactoryTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main': call.class";
    private static final JsonNode LIMITS_JSON = JsonNodeFactory.instance.objectNode().put("max", 10);
    private static final Limits LIMITS = new Limits(10);
    private static final List<Stub> CONVERT_STUBS = List.of(new Stub(new MethodRef("convert", null), null,
            new StubAnswer.Returns(IntNode.valueOf(42))));

    private ValueConverters mockConverters;
    private StubApplier mockStubs;
    private StubErrors errors;

    private UnitInstanceFactory underTest;

    @BeforeEach
    void setUp() {
        mockConverters = mock(ValueConverters.class);
        mockStubs = mock(StubApplier.class);
        errors = new StubErrors();
        underTest = new UnitInstanceFactory(mockConverters, mockStubs);
    }

    @Test
    void create_whenOnlySomeDepsAreListed_shouldConvertValuesAndMockTheRest() {
        when(mockConverters.fromYaml(LIMITS_JSON, Limits.class, WHERE + ", deps.limits.value")).thenReturn(LIMITS);

        UnitInstance result = underTest.create(Formatter.class, null,
                Map.of("limits", new Dependency.Value(LIMITS_JSON)), errors, WHERE);

        Formatter formatter = (Formatter) result.instance();
        assertThat(formatter.limits()).isSameAs(LIMITS);
        assertThat(Mockito.mockingDetails(formatter.ctaIds()).isMock()).isTrue();
        assertThat(formatter.ctaIds().convert("x")).isZero();
        assertThat(result.mocks()).containsOnlyKeys("ctaIds").containsEntry("ctaIds", formatter.ctaIds());
    }

    @Test
    void create_whenDepIsKeyedByAUniqueType_shouldStubAMockForThatParameter() {
        UnitInstance result = underTest.create(Formatter.class, null,
                Map.of("CtaIdConverter", new Dependency.Stubbed(CONVERT_STUBS)), errors, WHERE);

        Formatter formatter = (Formatter) result.instance();
        verify(mockStubs).apply(formatter.ctaIds(), CtaIdConverter.class, CONVERT_STUBS, errors,
                WHERE + ", deps.CtaIdConverter");
        assertThat(result.mocks()).containsOnlyKeys("CtaIdConverter", "limits");
    }

    @Test
    void create_whenDepIsKeyedByAQualifiedType_shouldMatchTheParameter() {
        ArgumentCaptor<Object> stubbed = ArgumentCaptor.forClass(Object.class);

        UnitInstance result = underTest.create(Formatter.class, null,
                Map.of(CtaIdConverter.class.getName(), new Dependency.Stubbed(CONVERT_STUBS)), errors, WHERE);

        verify(mockStubs).apply(stubbed.capture(), eq(CtaIdConverter.class), eq(CONVERT_STUBS), eq(errors),
                eq(WHERE + ", deps." + CtaIdConverter.class.getName()));
        assertThat(stubbed.getValue()).isSameAs(((Formatter) result.instance()).ctaIds());
    }

    @Test
    void create_whenDepIsReal_shouldBuildItByTheSameRules() {
        UnitInstance result = underTest.create(PriceQuote.class, null, Map.of("policy", new Dependency.Real()),
                errors, WHERE);

        Policy policy = ((PriceQuote) result.instance()).policy();
        assertThat(Mockito.mockingDetails(policy).isMock()).isFalse();
        assertThat(Mockito.mockingDetails(policy.rates()).isMock()).isTrue();
        assertThat(result.mocks()).isEmpty();
    }

    @Test
    void create_whenConstructorIsChosen_shouldUseIt() {
        when(mockConverters.fromYaml(TextNode.valueOf("Hi"), String.class, WHERE + ", deps.greeting.value"))
                .thenReturn("Hi");

        UnitInstance result = underTest.create(Greeter.class, List.of("java.lang.String"),
                Map.of("greeting", new Dependency.Value(TextNode.valueOf("Hi"))), errors, WHERE);

        assertThat(((Greeter) result.instance()).greeting()).isEqualTo("Hi");
    }

    @Test
    void create_whenSeveralConstructorsAndNoChoice_shouldListThem() {
        assertThatThrownBy(() -> underTest.create(Greeter.class, null, Map.of(), errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageStartingWith(WHERE + ": " + Greeter.class.getName() + " has 2 constructors; choose one"
                        + " with `constructor`: ")
                .hasMessageContaining("Greeter()")
                .hasMessageContaining("Greeter(String)");
    }

    @Test
    void create_whenChosenConstructorDoesNotExist_shouldListTheConstructors() {
        assertThatThrownBy(() -> underTest.create(Greeter.class, List.of("int"), Map.of(), errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("no constructor [int] on " + Greeter.class.getName());
    }

    @Test
    void create_whenDepMatchesNoParameter_shouldNameTheKeyAndTheParameters() {
        assertThatThrownBy(() -> underTest.create(Formatter.class, null,
                Map.of("clock", new Dependency.Real()), errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": deps 'clock' matches no constructor parameter of " + Formatter.class.getName()
                        + " by name or type; parameters: ctaIds (CtaIdConverter), limits (Limits)");
    }

    @Test
    void create_whenDepTypeIsAmbiguous_shouldNameBothParameters() {
        assertThatThrownBy(() -> underTest.create(Range.class, null,
                Map.of("Limits", new Dependency.Value(LIMITS_JSON)), errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": deps 'Limits' is ambiguous: parameters lower and upper both have type Limits;"
                        + " use a parameter name");
    }

    @Test
    void create_whenTwoKeysMatchOneParameter_shouldFail() {
        Map<String, Dependency> deps = new LinkedHashMap<>();
        deps.put("ctaIds", new Dependency.Real());
        deps.put("CtaIdConverter", new Dependency.Real());

        assertThatThrownBy(() -> underTest.create(Formatter.class, null, deps, errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": deps 'ctaIds' and 'CtaIdConverter' both match parameter ctaIds"
                        + " (CtaIdConverter)");
    }

    @Test
    void create_whenAnUnlistedParameterCantBeMocked_shouldAskForAValue() {
        assertThatThrownBy(() -> underTest.create(Counter.class, null, Map.of(), errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageStartingWith(WHERE + ": parameter start (int) can't be mocked; list it under `deps`");
        verify(mockStubs, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void create_whenARealDepIsTheClassItself_shouldFailNamingTheCycle() {
        assertThatThrownBy(() -> underTest.create(Node.class, null, Map.of("next", new Dependency.Real()), errors,
                WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ", deps.next (real): `real: true` dependencies form a cycle through "
                        + Node.class.getName());
    }

    @Test
    void create_whenARealDepHasAParameterThatCantBeMocked_shouldSuggestAValueForTheDep() {
        assertThatThrownBy(() -> underTest.create(CounterHolder.class, null,
                Map.of("counter", new Dependency.Real()), errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageStartingWith(WHERE + ", deps.counter (real): parameter start (int) can't be mocked;"
                        + " `real: true` builds Counter with mocks for all its parameters; give this dependency as a"
                        + " `value` instead");
    }

    @Test
    void create_whenTheConstructorThrows_shouldFailNamingTheLocationAndKeepTheCause() {
        assertThatThrownBy(() -> underTest.create(Exploding.class, null, Map.of(), errors, WHERE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(WHERE + ": constructor Exploding() threw java.lang.IllegalArgumentException: no config")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    public interface CtaIdConverter {

        int convert(String cta);
    }

    public record Limits(int max) {
    }

    public record Formatter(CtaIdConverter ctaIds, Limits limits) {
    }

    public record Range(Limits lower, Limits upper) {
    }

    public record Counter(int start) {
    }

    public record Policy(CtaIdConverter rates) {
    }

    public record PriceQuote(Policy policy) {
    }

    public record Node(Node next) {
    }

    public record CounterHolder(Counter counter) {
    }

    public static final class Exploding {

        public Exploding() {
            throw new IllegalArgumentException("no config");
        }
    }

    public static final class Greeter {

        private final String greeting;

        public Greeter() {
            this("Hello");
        }

        public Greeter(String greeting) {
            this.greeting = greeting;
        }

        String greeting() {
            return greeting;
        }
    }
}
