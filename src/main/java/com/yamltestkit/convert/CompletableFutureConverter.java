package com.yamltestkit.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.yamltestkit.infrastructure.reflect.GenericTypes;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@code CompletableFuture<T>} (and the {@code Future} / {@code CompletionStage} it implements): a YAML value
 * becomes a future completed with the converted content. In JSON a future is its result, waiting up to
 * {@link #COMPLETION_WAIT} for it.
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public final class CompletableFutureConverter implements ValueConverter {

    /** How long a returned future is awaited, here and when a call's outcome is decided. */
    public static final Duration COMPLETION_WAIT = Duration.ofSeconds(10);

    @Override
    public boolean supports(Type type) {
        Class<?> raw = GenericTypes.rawClass(type);
        return raw != Object.class
                && (raw.isAssignableFrom(CompletableFuture.class) || CompletableFuture.class.isAssignableFrom(raw));
    }

    @Override
    public Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
        return CompletableFuture.completedFuture(
                ctx.fromYaml(value, GenericTypes.typeArgument(targetType, CompletableFuture.class, 0)));
    }

    @Override
    public JsonNode toJson(Object value, ConversionContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) value;
        try {
            return ctx.toJson(future.get(COMPLETION_WAIT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            throw new ConversionException("CompletableFuture did not complete within " + COMPLETION_WAIT, e);
        } catch (ExecutionException e) {
            throw new ConversionException("CompletableFuture completed exceptionally: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversionException("interrupted while waiting for a CompletableFuture", e);
        }
    }

    @Override
    public Optional<Type> wrappedType(Type targetType) {
        return Optional.of(GenericTypes.typeArgument(targetType, CompletableFuture.class, 0));
    }

    @Override
    public Object wrap(Object value, Type targetType, ConversionContext ctx) {
        return CompletableFuture.completedFuture(value);
    }
}
