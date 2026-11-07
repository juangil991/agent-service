package com.vidacruz.agent.infrastructure.adapter.out.embedding;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

final class EmbeddingRequestRateLimiter {

    private final long intervalNanos;
    private final LongSupplier nanoTime;

    private long nextRequestAtNanos;
    private boolean hasScheduledRequest;

    EmbeddingRequestRateLimiter(Duration interval) {
        this(interval, System::nanoTime);
    }

    EmbeddingRequestRateLimiter(Duration interval, LongSupplier nanoTime) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("El intervalo entre solicitudes debe ser positivo");
        }
        this.intervalNanos = interval.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    Mono<Void> acquire() {
        return Mono.defer(() -> {
            long delayNanos;
            synchronized (this) {
                long now = nanoTime.getAsLong();
                delayNanos = hasScheduledRequest ? Math.max(0, nextRequestAtNanos - now) : 0;
                nextRequestAtNanos = now + delayNanos + intervalNanos;
                hasScheduledRequest = true;
            }
            return delayNanos == 0
                    ? Mono.empty()
                    : Mono.delay(Duration.ofNanos(delayNanos)).then();
        });
    }
}
