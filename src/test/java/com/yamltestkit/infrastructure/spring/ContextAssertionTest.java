package com.yamltestkit.infrastructure.spring;

import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.ContextExpectation.Presence;
import com.yamltestkit.domain.model.ContextExpectation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextAssertionTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main'";

    private ApplicationContext mockContext;

    private ContextAssertion underTest;

    @BeforeEach
    void setUp() {
        mockContext = mock(ApplicationContext.class);
        when(mockContext.containsBean("feedCache")).thenReturn(true);
        when(mockContext.getType("feedCache")).thenAnswer(invocation -> FeedCache.class);
        when(mockContext.getBeanNamesForType(FeedCache.class)).thenReturn(new String[]{"feedCache"});
        when(mockContext.getBeanNamesForType(EbayConnector.class)).thenReturn(new String[0]);
        underTest = new ContextAssertion();
    }

    @Test
    void check_whenBeansAndTypesAreAsExpected_shouldPass() {
        ContextExpectation expected = new ContextExpectation(new Presence(List.of("feedCache"), List.of("ebayClient")),
                new Presence(List.of(FeedCache.class.getName()), List.of(EbayConnector.class.getName())));

        List<AssertionFailedError> result = underTest.check(expected, mockContext, WHERE);

        assertThat(result).isEmpty();
    }

    @Test
    void check_whenBeansAreNotAsExpected_shouldReportEachName() {
        ContextExpectation expected = new ContextExpectation(new Presence(List.of("ebayClient"), List.of("feedCache")),
                Presence.NONE);

        List<AssertionFailedError> result = underTest.check(expected, mockContext, WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": context: expected bean 'ebayClient' but the context has no bean of that name",
                WHERE + ": context: expected no bean 'feedCache' but the context has one of type "
                        + FeedCache.class.getName());
    }

    @Test
    void check_whenBeanTypesAreNotAsExpected_shouldReportEachType() {
        ContextExpectation expected = new ContextExpectation(Presence.NONE,
                new Presence(List.of(EbayConnector.class.getName()), List.of(FeedCache.class.getName())));

        List<AssertionFailedError> result = underTest.check(expected, mockContext, WHERE);

        assertThat(result).extracting(Throwable::getMessage).containsExactly(
                WHERE + ": context: expected a bean of type " + EbayConnector.class.getName()
                        + " but the context has none",
                WHERE + ": context: expected no bean of type " + FeedCache.class.getName()
                        + " but the context has [feedCache]");
    }

    @Test
    void check_whenABeanTypeDoesNotExist_shouldFailAsAnInvalidCase() {
        ContextExpectation expected = new ContextExpectation(Presence.NONE,
                new Presence(List.of(), List.of("com.example.EbayConectr")));

        assertThatThrownBy(() -> underTest.check(expected, mockContext, WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageStartingWith(WHERE + ": context.beanTypes: class com.example.EbayConectr not found");
    }

    static final class FeedCache {
    }

    static final class EbayConnector {
    }
}
