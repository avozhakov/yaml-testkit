package com.yamltestkit.infrastructure.mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.ThrowSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses the real converter chain for constructor arguments (deterministic value mapping, like test data).
 */
class ExceptionFactoryTest {

    private static final String WHERE = "cases/a.yaml, case 'c', step 'main', stub fetch";
    private static final String API_EXCEPTION = ApiException.class.getName();
    private static final ThrowSpec TIMEOUT = new ThrowSpec(HttpTimeoutException.class.getName(), "t/o", List.of(),
            null);

    private ExceptionFactory underTest;

    @BeforeEach
    void setUp() {
        underTest = new ExceptionFactory(ValueConverters.create(List.of()));
    }

    @Test
    void create_whenArgsFitATypedConstructor_shouldUseIt() {
        Throwable result = underTest.create(new ThrowSpec(API_EXCEPTION, null,
                List.<JsonNode>of(IntNode.valueOf(500), TextNode.valueOf("boom")), null), WHERE);

        assertThat(result).isInstanceOf(ApiException.class).hasMessage("boom").hasNoCause();
        assertThat(((ApiException) result).code()).isEqualTo(500);
    }

    @Test
    void create_whenOnlyACauseIsGiven_shouldUseTheThrowableConstructor() {
        Throwable result = underTest.create(new ThrowSpec(API_EXCEPTION, null, List.of(), TIMEOUT), WHERE);

        assertThat(result).isInstanceOf(ApiException.class).hasCauseInstanceOf(HttpTimeoutException.class);
        assertThat(result.getCause()).hasMessage("t/o");
    }

    @Test
    void create_whenMessageAndCauseAreGiven_shouldUseTheMessageCauseConstructor() {
        Throwable result = underTest.create(new ThrowSpec(IllegalStateException.class.getName(), "db down", List.of(),
                TIMEOUT), WHERE);

        assertThat(result).isInstanceOf(IllegalStateException.class).hasMessage("db down")
                .hasCauseInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void create_whenNoConstructorTakesTheCause_shouldSetItWithInitCause() {
        Throwable result = underTest.create(new ThrowSpec(HttpTimeoutException.class.getName(), "outer", List.of(),
                new ThrowSpec(IllegalStateException.class.getName(), "inner", List.of(), null)), WHERE);

        assertThat(result).hasMessage("outer").hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void create_whenNoConstructorFits_shouldListTheConstructors() {
        assertThatThrownBy(() -> underTest.create(new ThrowSpec(API_EXCEPTION, null,
                List.<JsonNode>of(TextNode.valueOf("x"), TextNode.valueOf("y"), TextNode.valueOf("z")), null), WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageStartingWith(WHERE + ": no public constructor of " + API_EXCEPTION)
                .hasMessageContaining("ApiException(int, String)")
                .hasMessageContaining("ApiException(Throwable)");
    }

    @Test
    void create_whenANumberMeetsAStringParameter_shouldNotConvertIt() {
        assertThatThrownBy(() -> underTest.create(new ThrowSpec(API_EXCEPTION, null,
                List.<JsonNode>of(TextNode.valueOf("500"), IntNode.valueOf(7)), null), WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessageContaining("no public constructor");
    }

    @Test
    void create_whenTypeIsNotAThrowable_shouldFailNamingIt() {
        assertThatThrownBy(() -> underTest.create(new ThrowSpec(String.class.getName(), "x", List.of(), null), WHERE))
                .isInstanceOf(CaseDefinitionException.class)
                .hasMessage(WHERE + ": java.lang.String is not a Throwable");
    }

    public static final class ApiException extends Exception {

        private final int code;

        public ApiException(int code, String message) {
            super(message);
            this.code = code;
        }

        public ApiException(Throwable cause) {
            super(cause);
            this.code = 0;
        }

        int code() {
            return code;
        }
    }
}
