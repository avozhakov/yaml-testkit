package com.yamltestkit.infrastructure.mockito;

import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.domain.model.ArgMatcher;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.MethodRef;
import com.yamltestkit.domain.model.Stub;
import com.yamltestkit.domain.model.StubAnswer;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The answer and matcher factories are mocked; the objects being stubbed are real Mockito mocks and spies, since
 * registering stubs on them is what the applier does.
 */
class StubApplierTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main', deps.providers";
    private static final String FIND_WHERE = WHERE + ", stub find";
    private static final Provider AMAZON = new Provider("amazon", 4);
    private static final StubAnswer RETURNS_AMAZON = new StubAnswer.Returns(TextNode.valueOf("amazon"));
    private static final List<ArgMatcher> AMAZON_ARGS = List.of(new ArgMatcher.Literal(TextNode.valueOf("amazon")));
    private static final Answer<Object> AMAZON_ANSWER = invocation -> Optional.of(AMAZON);
    private static final List<ArgumentMatcher<Object>> AMAZON_MATCHERS = List.of("amazon"::equals);
    private static final Type PROVIDER_LIST_SUPPLIER = GenericTypes.parameterized(Supplier.class,
            GenericTypes.parameterized(List.class, Provider.class));

    private AnswerFactory mockAnswers;
    private ArgMatcherFactory mockMatchers;
    private Providers mockProviders;
    private StubErrors errors;

    private StubApplier underTest;

    @BeforeEach
    void setUp() {
        mockAnswers = mock(AnswerFactory.class);
        mockMatchers = mock(ArgMatcherFactory.class);
        mockProviders = mock(Providers.class);
        errors = new StubErrors();
        underTest = new StubApplier(mockAnswers, mockMatchers);
    }

    @Test
    void apply_whenStubIsGiven_shouldAnswerMatchingCallsAndLeaveOthersDefault() throws Exception {
        Method find = Providers.class.getMethod("find", String.class);
        when(mockAnswers.answer(RETURNS_AMAZON, find, Providers.class, FIND_WHERE)).thenReturn(AMAZON_ANSWER);
        when(mockMatchers.forMethod(AMAZON_ARGS, find, Providers.class, errors, FIND_WHERE))
                .thenReturn(AMAZON_MATCHERS);

        underTest.apply(mockProviders, Providers.class, List.of(new Stub(new MethodRef("find", null), AMAZON_ARGS,
                RETURNS_AMAZON)), errors, WHERE);

        assertThat(mockProviders.find("amazon")).contains(AMAZON);
        assertThat(mockProviders.find("ebay")).isEmpty();
    }

    @Test
    void apply_whenTargetIsASpy_shouldNotInvokeTheRealMethodWhileStubbing() throws Exception {
        CountingService spyService = spy(new CountingService());
        Method greet = CountingService.class.getMethod("greet", String.class);
        when(mockAnswers.answer(RETURNS_AMAZON, greet, CountingService.class, WHERE + ", stub greet"))
                .thenReturn(invocation -> "stubbed");
        when(mockMatchers.forMethod(AMAZON_ARGS, greet, CountingService.class, errors, WHERE + ", stub greet"))
                .thenReturn(AMAZON_MATCHERS);

        underTest.apply(spyService, CountingService.class, List.of(new Stub(new MethodRef("greet", null),
                AMAZON_ARGS, RETURNS_AMAZON)), errors, WHERE);

        assertThat(spyService.calls()).isZero();
        assertThat(spyService.greet("amazon")).isEqualTo("stubbed");
        assertThat(spyService.greet("bo")).isEqualTo("hi bo");
    }

    @Test
    void apply_whenMethodIsFinal_shouldStubIt() throws Exception {
        CountingService mockService = mock(CountingService.class);
        Method sealed = CountingService.class.getMethod("sealed");
        when(mockAnswers.answer(RETURNS_AMAZON, sealed, CountingService.class, WHERE + ", stub sealed"))
                .thenReturn(invocation -> "stubbed");
        when(mockMatchers.forMethod(null, sealed, CountingService.class, errors, WHERE + ", stub sealed"))
                .thenReturn(List.of());

        underTest.apply(mockService, CountingService.class, List.of(new Stub(new MethodRef("sealed", null), null,
                RETURNS_AMAZON)), errors, WHERE);

        assertThat(mockService.sealed()).isEqualTo("stubbed");
    }

    @Test
    void apply_whenDeclaredTypeIsGeneric_shouldHandItToTheFactories() throws Exception {
        @SuppressWarnings("unchecked")
        Supplier<List<Provider>> mockSupplier = mock(Supplier.class);
        Method get = Supplier.class.getMethod("get");
        when(mockAnswers.answer(RETURNS_AMAZON, get, PROVIDER_LIST_SUPPLIER, WHERE + ", stub get"))
                .thenReturn(invocation -> List.of(AMAZON));
        when(mockMatchers.forMethod(null, get, PROVIDER_LIST_SUPPLIER, errors, WHERE + ", stub get"))
                .thenReturn(List.of());

        underTest.apply(mockSupplier, PROVIDER_LIST_SUPPLIER, List.of(new Stub(new MethodRef("get", null), null,
                RETURNS_AMAZON)), errors, WHERE);

        assertThat(mockSupplier.get()).containsExactly(AMAZON);
    }

    @Test
    void apply_whenMethodIsOverloaded_shouldRequireParamTypes() {
        Stub ambiguous = new Stub(new MethodRef("count", null), null, RETURNS_AMAZON);

        assertThatThrownBy(() -> underTest.apply(mockProviders, Providers.class, List.of(ambiguous), errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("is ambiguous; add `paramTypes` to choose one of:")
                .hasMessageContaining("count(String)")
                .hasMessageContaining("count()");
        verify(mockAnswers, never()).answer(any(), any(), any(), any());
    }

    @Test
    void apply_whenMethodIsStatic_shouldFailNamingIt() {
        Stub stub = new Stub(new MethodRef("none", null), null, RETURNS_AMAZON);

        assertThatThrownBy(() -> underTest.apply(mockProviders, Providers.class, List.of(stub), errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ", stub none: static none() is static; static methods can't be stubbed");
        verify(mockAnswers, never()).answer(any(), any(), any(), any());
    }

    @Test
    void apply_whenMethodIsPrivate_shouldFailNamingIt() {
        Stub stub = new Stub(new MethodRef("secret", null), null, RETURNS_AMAZON);

        assertThatThrownBy(() -> underTest.apply(mock(CountingService.class), CountingService.class, List.of(stub),
                errors, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ", stub secret: secret() is private; private methods can't be stubbed");
        verify(mockAnswers, never()).answer(any(), any(), any(), any());
    }

    record Provider(String name, int maxAds) {
    }

    public interface Providers {

        static Providers none() {
            return null;
        }

        Optional<Provider> find(String name);

        int count();

        int count(String region);
    }

    public static class CountingService {

        private int calls;

        public String greet(String name) {
            calls++;
            return "hi " + name + secret();
        }

        public final String sealed() {
            return "real";
        }

        private String secret() {
            return "";
        }

        int calls() {
            return calls;
        }
    }
}
