package com.yamltestkit.engine;

import com.yamltestkit.convert.ValueConverters;
import com.yamltestkit.domain.assertion.CheckResult;
import com.yamltestkit.domain.model.CallCount;
import com.yamltestkit.domain.model.CaseDefinitionException;
import com.yamltestkit.domain.model.Expect;
import com.yamltestkit.domain.model.Step;
import com.yamltestkit.domain.model.VerifyCheck;
import com.yamltestkit.infrastructure.logback.LogCapture;
import com.yamltestkit.infrastructure.mockito.MockVerifier;
import com.yamltestkit.infrastructure.spring.ContextAssertion;
import com.yamltestkit.infrastructure.spring.OverriddenBean;
import com.yamltestkit.infrastructure.wiremock.JournalVerifier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.mockito.Mockito;
import org.opentest4j.AssertionFailedError;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Checks what a step's call did besides its response or return value: the {@code verify} checks in list order, each
 * polled when it has {@code within} (until it holds, or for the whole duration when it expects no call), its
 * captured calls kept under its {@code as} name for later references; the {@code logs} expected during the case so
 * far; and the {@code context} beans. Step executors run these after the call and before their own expectations,
 * which may reference the captures.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class EffectChecks {

    static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final MockVerifier mocks;
    private final JournalVerifier journals;
    private final LogCapture logs;
    private final ContextAssertion contexts;

    public static EffectChecks create(ValueConverters converters, LogCapture logs) {
        return new EffectChecks(MockVerifier.create(converters), JournalVerifier.create(), logs,
                new ContextAssertion());
    }

    /**
     * @return the failed checks, empty when all hold
     * @throws CaseDefinitionException when a check names a dependency the step's instance has no mock for, or is
     *                                 invalid for its target
     */
    public List<AssertionFailedError> check(CaseRun run, Step step) {
        String where = run.where(step);
        Expect expect = step.expect();
        List<AssertionFailedError> failures = new ArrayList<>();
        for (int i = 0; i < expect.verify().size(); i++) {
            VerifyCheck check = expect.verify().get(i);
            String at = where + ": verify[" + i + "] " + describe(check);
            CheckResult result = poll(check, () -> verify(check, run, step, at));
            if (check.as() != null && result.captured() != null) {
                run.recordCapture(check.as(), result.captured());
            }
            failures.addAll(result.failures());
        }
        failures.addAll(logs.check(expect.logs(), where));
        if (expect.context() != null) {
            failures.addAll(contexts.check(expect.context(), run.caseContext().running().context(), where));
        }
        return failures;
    }

    private CheckResult verify(VerifyCheck check, CaseRun run, Step step, String where) {
        return switch (check) {
            case VerifyCheck.OnService onService -> journals.verify(onService, run.caseContext().wireMock(), run,
                    where);
            case VerifyCheck.OnMock onMock when onMock.target() == VerifyCheck.MockTarget.BEAN -> {
                OverriddenBean bean = run.caseContext().running().overridden(onMock.name(), where);
                yield mocks.verify(onMock, bean.instance(), bean.declaredType(), run, run.stubErrors(), where);
            }
            case VerifyCheck.OnMock onMock -> {
                Object mock = dependency(run.unitMocks(step.name()), onMock.name(), where);
                yield mocks.verify(onMock, mock, Mockito.mockingDetails(mock).getMockCreationSettings().getTypeToMock(),
                        run, run.stubErrors(), where);
            }
        };
    }

    private static Object dependency(Map<String, Object> mocks, String name, String where) {
        Object mock = mocks.get(name);
        if (mock == null) {
            throw new CaseDefinitionException(where + ": the instance has no mock dependency '" + name
                    + "'; its mocks (by deps key or parameter name): " + new TreeSet<>(mocks.keySet()));
        }
        return mock;
    }

    /**
     * Runs the check once, or with {@code within} polls it every 100 ms: a check expecting calls until it holds or
     * time runs out, a check expecting none ({@code never}, {@code times: 0}, {@code noInteractions}) for the whole
     * duration, failing as soon as a call arrives. Reports the last failures.
     */
    private static CheckResult poll(VerifyCheck check, Supplier<CheckResult> verification) {
        Duration within = check.within();
        if (within == null) {
            return verification.get();
        }
        return expectsNoCalls(check.count()) ? holdFor(within, verification) : waitFor(within, verification);
    }

    private static CheckResult waitFor(Duration within, Supplier<CheckResult> verification) {
        long deadline = System.nanoTime() + within.toNanos();
        CheckResult result = verification.get();
        while (!result.passed() && System.nanoTime() + POLL_INTERVAL.toNanos() <= deadline) {
            sleep(POLL_INTERVAL.toNanos());
            result = verification.get();
        }
        return result.passed() ? result : withNote(result, " (still failing after waiting " + within.toMillis()
                + " ms, within)");
    }

    /** The last check runs once the duration has passed, so a call arriving late in the window still fails it. */
    private static CheckResult holdFor(Duration within, Supplier<CheckResult> verification) {
        long deadline = System.nanoTime() + within.toNanos();
        CheckResult result = verification.get();
        long remaining = deadline - System.nanoTime();
        while (result.passed() && remaining > 0) {
            sleep(Math.min(POLL_INTERVAL.toNanos(), remaining));
            result = verification.get();
            remaining = deadline - System.nanoTime();
        }
        return result.passed() ? result : withNote(result, " (a call arrived while waiting " + within.toMillis()
                + " ms, within)");
    }

    private static boolean expectsNoCalls(CallCount count) {
        return switch (count) {
            case CallCount.Times times -> times.times() == 0;
            case CallCount.AtLeast ignored -> false;
            case CallCount.NoInteractions ignored -> true;
        };
    }

    private static CheckResult withNote(CheckResult result, String note) {
        List<AssertionFailedError> failures = result.failures().stream()
                .map(failure -> new AssertionFailedError(failure.getMessage() + note, failure))
                .toList();
        return new CheckResult(failures, result.captured());
    }

    private static void sleep(long nanos) {
        try {
            Thread.sleep(Duration.ofNanos(nanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while polling a verify check", e);
        }
    }

    private static String describe(VerifyCheck check) {
        return switch (check) {
            case VerifyCheck.OnService onService -> "service '" + onService.service() + "'";
            case VerifyCheck.OnMock onMock -> (onMock.target() == VerifyCheck.MockTarget.BEAN ? "bean '" : "dep '")
                    + onMock.name() + "'" + (onMock.method() == null ? "" : " " + onMock.method().name());
        };
    }
}
