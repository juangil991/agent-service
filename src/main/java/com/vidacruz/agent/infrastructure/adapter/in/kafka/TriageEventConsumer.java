package com.vidacruz.agent.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.agent.application.port.in.ProjectTriageEventUseCase;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.kafka.receiver.KafkaReceiver;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class TriageEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TriageEventConsumer.class);

    private final KafkaReceiver<String, String> receiver;
    private final ObjectMapper objectMapper;
    private final ProjectTriageEventUseCase projectEvent;
    private Disposable subscription;

    public TriageEventConsumer(
            KafkaReceiver<String, String> receiver,
            ObjectMapper objectMapper,
            ProjectTriageEventUseCase projectEvent) {
        this.receiver = receiver;
        this.objectMapper = objectMapper;
        this.projectEvent = projectEvent;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        subscription = receiver.receive()
                .concatMap(record -> project(record.value())
                        .then(record.receiverOffset().commit())
                        .doOnError(error -> logger.error(
                                "No se pudo proyectar el evento Kafka en offset {}",
                                record.receiverOffset().offset(),
                                error)))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(retry -> logger.warn(
                                "Se reintentará el consumidor de eventos de triaje",
                                retry.failure())))
                .subscribe(
                        ignored -> { },
                        error -> logger.error("El consumidor de eventos de triaje terminó", error));
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private Mono<Void> project(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, TriageEventMessage.class))
                .flatMap(message -> projectEvent.project(message.toDomain()));
    }
}
