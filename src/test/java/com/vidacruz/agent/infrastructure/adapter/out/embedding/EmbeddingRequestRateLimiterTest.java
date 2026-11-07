package com.vidacruz.agent.infrastructure.adapter.out.embedding;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingRequestRateLimiterTest {

    @Test
    void spacesConcurrentRequestsToStayWithinThreePerRollingMinute() {
        List<Long> requestTimes = new ArrayList<>();

        StepVerifier.withVirtualTime(() -> {
            VirtualTimeScheduler scheduler = VirtualTimeScheduler.getOrSet();
            EmbeddingRequestRateLimiter limiter = new EmbeddingRequestRateLimiter(
                    Duration.ofSeconds(21),
                    () -> scheduler.now(TimeUnit.NANOSECONDS));
            return Mono.when(
                    trackRequest(limiter, scheduler, requestTimes),
                    trackRequest(limiter, scheduler, requestTimes),
                    trackRequest(limiter, scheduler, requestTimes),
                    trackRequest(limiter, scheduler, requestTimes));
        })
                .thenAwait(Duration.ofSeconds(63))
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertThat(requestTimes).containsExactly(
                0L,
                Duration.ofSeconds(21).toNanos(),
                Duration.ofSeconds(42).toNanos(),
                Duration.ofSeconds(63).toNanos());
    }

    private Mono<Void> trackRequest(
            EmbeddingRequestRateLimiter limiter,
            VirtualTimeScheduler scheduler,
            List<Long> requestTimes) {
        return limiter.acquire()
                .doOnSuccess(ignored -> requestTimes.add(scheduler.now(TimeUnit.NANOSECONDS)));
    }
}
