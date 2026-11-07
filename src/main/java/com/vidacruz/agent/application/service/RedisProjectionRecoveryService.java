package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.port.out.TriageEventReplayPort;
import com.vidacruz.agent.application.port.out.UrgencyProjectionRecoveryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class RedisProjectionRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(RedisProjectionRecoveryService.class);
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final UrgencyProjectionRecoveryPort projection;
    private final TriageEventReplayPort replay;

    public RedisProjectionRecoveryService(
            UrgencyProjectionRecoveryPort projection,
            TriageEventReplayPort replay) {
        this.projection = projection;
        this.replay = replay;
    }

    public Mono<Void> recoverIfNeeded() {
        return Mono.defer(() -> projection.isComplete()
                        .flatMap(complete -> complete
                                ? Mono.empty()
                                : rebuildOnce()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, INITIAL_RETRY_DELAY)
                        .maxBackoff(MAX_RETRY_DELAY)
                        .doBeforeRetry(retry -> logger.warn(
                                "Redis projection recovery failed; retrying while the Agent remains not-ready",
                                retry.failure())));
    }

    private Mono<Void> rebuildOnce() {
        return Mono.defer(() -> projection.clear()
                        .then(replay.replay())
                        .then(projection.markComplete()))
                .doOnSubscribe(ignored -> logger.info("Redis projection is incomplete; starting Kafka replay"))
                .doOnSuccess(ignored -> logger.info("Redis projection recovery completed"))
                .doOnError(error -> logger.error("Redis projection recovery attempt failed", error));
    }
}
