package com.yamltestkit.infrastructure.mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.domain.model.ThrowSpec;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.yamltestkit.domain.json.JsonTestFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uses the real converter chain (design D17); the exception factory and the invocation are mocked.
 */
class AnswerFactoryTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main', deps.providers, stub m";
    private static final Provider AMAZON = new Provider("amazon", 4);
    private static final JsonNode AMAZON_JSON = json("{\"name\": \"amazon\", \"maxAds\": 4}");
    private static final Type PROVIDER_LIST_SUPPLIER = GenericTypes.parameterized(Supplier.class,
            GenericTypes.parameterized(List.class, Provider.class));
    private static final ThrowSpec BOOM = new ThrowSpec(IllegalStateException.class.getName(), "boom", List.of(),
            null);
    private static final ThrowSpec CHECKED = new ThrowSpec(Exception.class.getName(), "checked", List.of(), null);

    private ExceptionFactory mockExceptions;
    private InvocationOnMock mockInvocation;

    private AnswerFactory underTest;

    @BeforeEach
    void setUp() {
        mockExceptions = mock(ExceptionFactory.class);
        mockInvocation = mock(InvocationOnMock.class);
        underTest = new AnswerFactory(ValueConverters.create(List.of()), mockExceptions);
    }

    @Test
    void answer_whenValueIsReturned_shouldConvertItToTheReturnType() throws Throwable {
        Answer<Object> result = underTest.answer(new StubAnswer.Returns(AMAZON_JSON), method("same"), Providers.class,
                WHERE);

        assertThat(result.answer(mockInvocation)).isEqualTo(AMAZON);
    }

    @Test
    void answer_whenDeclaredTypeIsGeneric_shouldConvertToTheResolvedReturnType() throws Throwable {
        Method get = Supplier.class.getMethod("get");

        Answer<Object> result = underTest.answer(new StubAnswer.Returns(json("[" + AMAZON_JSON + "]")), get,
                PROVIDER_LIST_SUPPLIER, WHERE);

        assertThat(result.answer(mockInvocation)).isEqualTo(List.of(AMAZON));
    }

    @Test
    void answer_whenAVoidMethodReturnsAValue_shouldFail() {
        assertThatThrownBy(() -> underTest.answer(new StubAnswer.Returns(json("1")), method("reset"),
                Providers.class, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": reset() returns void; use `returns: null` or leave the method unstubbed");
    }

    @Test
    void answer_whenAVoidMethodReturnsNull_shouldAnswerNull() throws Throwable {
        Answer<Object> result = underTest.answer(new StubAnswer.Returns(NullNode.getInstance()), method("reset"),
                Providers.class, WHERE);

        assertThat(result.answer(mockInvocation)).isNull();
    }

    @Test
    void answer_whenArgumentFitsTheReturnType_shouldReturnTheSameInstance() throws Throwable {
        when(mockInvocation.getArgument(0)).thenReturn(AMAZON);

        Answer<Object> result = underTest.answer(new StubAnswer.ReturnsArg(0), method("same"), Providers.class,
                WHERE);

        assertThat(result.answer(mockInvocation)).isSameAs(AMAZON);
    }

    @Test
    void answer_whenReturnTypeWrapsTheArgument_shouldWrapIt() throws Throwable {
        when(mockInvocation.getArgument(0)).thenReturn(AMAZON);

        Answer<Object> result = underTest.answer(new StubAnswer.ReturnsArg(0), method("wrapped"), Providers.class,
                WHERE);

        assertThat(result.answer(mockInvocation)).isEqualTo(Optional.of(AMAZON));
    }

    @Test
    void answer_whenReturnsArgIndexIsOutOfRange_shouldFailWhileStubbing() {
        assertThatThrownBy(() -> underTest.answer(new StubAnswer.ReturnsArg(1), method("same"), Providers.class,
                WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": returnsArg 1 is out of range for same(AnswerFactoryTest$Provider)");
    }

    @Test
    void answer_whenArgumentNeitherFitsNorWraps_shouldFailWhileStubbing() {
        assertThatThrownBy(() -> underTest.answer(new StubAnswer.ReturnsArg(0), method("describe"), Providers.class,
                WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": returnsArg 0 of describe(AnswerFactoryTest$Provider) is a "
                        + Provider.class.getName() + ", which is not a java.lang.String and can't be wrapped into one");
    }

    @Test
    void answer_whenWrapperContentDoesNotAcceptTheArgument_shouldFailWhileStubbing() {
        assertThatThrownBy(() -> underTest.answer(new StubAnswer.ReturnsArg(0), method("lookup"), Providers.class,
                WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("returnsArg 0 of lookup(String) is a java.lang.String, which is not a"
                        + " java.util.Optional<" + Provider.class.getName() + "> and can't be wrapped into one");
    }

    @Test
    void answer_whenExceptionIsUnchecked_shouldThrowANewOneOnEachCall() {
        IllegalStateException first = new IllegalStateException("boom");
        IllegalStateException second = new IllegalStateException("boom");
        when(mockExceptions.create(BOOM, WHERE + ", throws")).thenReturn(first, first, second);

        Answer<Object> result = underTest.answer(new StubAnswer.Throws(BOOM), method("fetch"), Providers.class,
                WHERE);

        assertThatThrownBy(() -> result.answer(mockInvocation)).isSameAs(first);
        assertThatThrownBy(() -> result.answer(mockInvocation)).isSameAs(second);
    }

    @Test
    void answer_whenACheckedExceptionIsNotDeclared_shouldFailWhileStubbing() {
        when(mockExceptions.create(CHECKED, WHERE + ", throws")).thenReturn(new Exception("checked"));

        assertThatThrownBy(() -> underTest.answer(new StubAnswer.Throws(CHECKED), method("same"), Providers.class,
                WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("does not declare the checked exception java.lang.Exception");
    }

    @Test
    void answer_whenAnswersAreConsecutive_shouldRepeatTheLastOne() throws Throwable {
        IllegalStateException boom = new IllegalStateException("boom");
        when(mockExceptions.create(BOOM, WHERE + ", throws")).thenReturn(boom);

        Answer<Object> result = underTest.answer(new StubAnswer.Consecutive(List.of(new StubAnswer.Throws(BOOM),
                new StubAnswer.Returns(TextNode.valueOf("ok")))), method("fetch"), Providers.class, WHERE);

        assertThatThrownBy(() -> result.answer(mockInvocation)).isSameAs(boom);
        assertThat(result.answer(mockInvocation)).isEqualTo("ok");
        assertThat(result.answer(mockInvocation)).isEqualTo("ok");
    }

    private static Method method(String name) {
        for (Method method : Providers.class.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new IllegalArgumentException("no method " + name);
    }

    record Provider(String name, int maxAds) {
    }

    public interface Providers {

        Provider same(Provider provider);

        Optional<Provider> wrapped(Provider provider);

        Optional<Provider> lookup(String name);

        String describe(Provider provider);

        String fetch(String id) throws IOException;

        void reset();
    }
}
