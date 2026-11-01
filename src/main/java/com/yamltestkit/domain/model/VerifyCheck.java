package com.yamltestkit.domain.model;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * One {@code expect.verify} check, on a mock / spy or on a WireMock service journal.
 */
public sealed interface VerifyCheck permits VerifyCheck.OnMock, VerifyCheck.OnService {

    CallCount count();

    /** Wait up to this long for the check to hold; {@code null} checks once. */
    @Nullable Duration within();

    /** Name under which the captured calls are kept for references; {@code null} when not captured. */
    @Nullable String as();

    List<Assertion> asserts();

    /**
     * Check on a bean ({@code bean:}) or a unit dependency ({@code dep:}).
     *
     * @param method  {@code null} only for {@link CallCount.NoInteractions}
     * @param args    per-argument filters, {@code null} when not given
     * @param argType keeps only calls whose argument is an instance of a type, {@code null} when not given
     */
    record OnMock(
            MockTarget target,
            String name,
            @Nullable MethodRef method,
            @Nullable List<ArgMatcher> args,
            @Nullable ArgTypeFilter argType,
            CallCount count,
            @Nullable Duration within,
            @Nullable String as,
            List<Assertion> asserts
    ) implements VerifyCheck {

        public OnMock {
            args = args == null ? null : List.copyOf(args);
            asserts = List.copyOf(asserts);
        }
    }

    record OnService(
            String service,
            RequestMatcher request,
            CallCount count,
            @Nullable Duration within,
            @Nullable String as,
            List<Assertion> asserts
    ) implements VerifyCheck {

        public OnService {
            asserts = List.copyOf(asserts);
        }
    }

    enum MockTarget {
        /** A Spring bean by name ({@code http} / {@code bean} cases). */
        BEAN,
        /** A constructor dependency of the unit ({@code class} cases). */
        DEP
    }

    /** {@code argType}: keep only calls whose argument at {@code index} is an instance of {@code type}. */
    record ArgTypeFilter(int index, String type) {
    }
}
