package com.yamltestkit.infrastructure.invocation;

import com.yamltestkit.convert.CompletableFutureConverter;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * What a {@code bean} / {@code class} call did: returned a value or threw.
 */
public sealed interface CallOutcome permits CallOutcome.Returned, CallOutcome.Thrown {

    /**
     * The outcome of a call that returned {@code value}. A returned {@code CompletableFuture} that completes
     * exceptionally (waiting up to {@link CompletableFutureConverter#COMPLETION_WAIT}) counts as the call throwing its
     * cause, so {@code expect.throws} can assert on it; one that doesn't complete in time stays a returned value,
     * whose conversion then reports the timeout.
     */
    static CallOutcome of(@Nullable Object value) {
        if (!(value instanceof CompletableFuture<?> future)) {
            return new Returned(value);
        }
        try {
            future.get(CompletableFutureConverter.COMPLETION_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            return new Returned(value);
        } catch (ExecutionException e) {
            return new Thrown(e.getCause());
        } catch (CancellationException e) {
            return new Thrown(e);
        } catch (TimeoutException e) {
            return new Returned(value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the returned CompletableFuture", e);
        }
    }

    record Returned(@Nullable Object value) implements CallOutcome {
    }

    record Thrown(Throwable exception) implements CallOutcome {
    }
}
