package com.vidacruz.agent.infrastructure.adapter.in.kafka;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.util.retry.Retry;
import reactor.kafka.receiver.KafkaReceiver;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class TriageEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TriageEventConsumer.class);

    private final KafkaReceiver<String, String> receiver;
    private final TriageEventProjector projector;
    private Disposable subscription;

    public TriageEventConsumer(
            KafkaReceiver<String, String> receiver,
            TriageEventProjector projector) {
        this.receiver = receiver;
        this.projector = projector;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        subscription = receiver.receive()
                .concatMap(record -> projector.project(record.value())
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

}
