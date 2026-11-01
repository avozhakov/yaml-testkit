package com.yamltestkit.infrastructure.mockito;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.CaseDefinitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses the real converter chain (design D17): literal matchers compare converted values as JSON.
 */
class ArgMatcherFactoryTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main', deps.providers, stub m";
    private static final long BIG_ID = 1234567890123456789L;

    private StubErrors errors;

    private ArgMatcherFactory underTest;

    @BeforeEach
    void setUp() {
        errors = new StubErrors();
        underTest = new ArgMatcherFactory(ValueConverters.create(List.of()));
    }

    @Test
    void forMethod_whenArgsAreAbsent_shouldMatchAnyArguments() throws Exception {
        List<ArgumentMatcher<Object>> result = underTest.forMethod(null, method("byProvider"), Providers.class,
                errors, WHERE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().matches(null)).isTrue();
    }

    @Test
    void forMethod_whenLiteralIsAnObject_shouldMatchAnEqualValueByJson() throws Exception {
        List<ArgumentMatcher<Object>> result = underTest.forMethod(
                List.of(new ArgMatcher.Literal(json("{\"name\": \"amazon\", \"maxAds\": 4}"))), method("byProvider"),
                Providers.class, errors, WHERE);

        assertThat(result.getFirst().matches(new Provider("amazon", 4))).isTrue();
        assertThat(result.getFirst().matches(new Provider("amazon", 5))).isFalse();
    }

    @Test
    void forMethod_whenLiteralIsA19DigitNumber_shouldMatchOnlyTheExactValue() throws Exception {
        List<ArgumentMatcher<Object>> result = underTest.forMethod(
                List.of(new ArgMatcher.Literal(json(Long.toString(BIG_ID)))), method("byId"), Providers.class, errors,
                WHERE);

        assertThat(result.getFirst().matches(BIG_ID)).isTrue();
        assertThat(result.getFirst().matches(BIG_ID - 1)).isFalse();
    }

    @Test
    void forMethod_whenMatcherIsAnyOrAnyOf_shouldMatchAnythingOrInstancesOfTheType() throws Exception {
        List<ArgumentMatcher<Object>> any = underTest.forMethod(List.of(new ArgMatcher.Any()), method("byValue"),
                Providers.class, errors, WHERE);
        List<ArgumentMatcher<Object>> anyOf = underTest.forMethod(
                List.of(new ArgMatcher.OfType(Integer.class.getName())), method("byValue"), Providers.class, errors,
                WHERE);

        assertThat(any.getFirst().matches(null)).isTrue();
        assertThat(anyOf.getFirst().matches(7)).isTrue();
        assertThat(anyOf.getFirst().matches("7")).isFalse();
    }

    @Test
    void forMethod_whenArgsCountDiffers_shouldFailNamingTheSignature() {
        assertThatThrownBy(() -> underTest.forMethod(List.of(new ArgMatcher.Any(), new ArgMatcher.Any()),
                method("byId"), Providers.class, errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": `args` has 2 matchers but byId(long) takes 1 arguments");
    }

    @Test
    void matches_whenTheActualArgumentCantBeConverted_shouldNotMatchAndRecordTheError() throws Exception {
        List<ArgumentMatcher<Object>> matchers = underTest.forMethod(List.of(new ArgMatcher.Literal(json("\"x\""))),
                method("byValue"), Providers.class, errors, WHERE);

        boolean result = matchers.getFirst().matches(CompletableFuture.failedFuture(new IllegalStateException("x")));

        assertThat(result).isFalse();
        assertThat(errors.drain()).singleElement().extracting(Throwable::getMessage).asString()
                .startsWith(WHERE + ", args[0]: the actual argument can't be compared with the stub's value: ");
    }

    private static Method method(String name) throws NoSuchMethodException {
        for (Method method : Providers.class.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    record Provider(String name, int maxAds) {
    }

    public interface Providers {

        String byProvider(Provider provider);

        String byId(long id);

        String byValue(Object value);
    }
}
