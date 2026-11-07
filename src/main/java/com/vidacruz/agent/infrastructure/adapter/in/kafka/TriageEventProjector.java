package com.vidacruz.agent.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.agent.application.port.in.ProjectTriageEventUseCase;
import com.vidacruz.agent.infrastructure.adapter.in.rest.CorrelationIdWebFilter;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TriageEventProjector {

    private static final Logger logger = LoggerFactory.getLogger(TriageEventProjector.class);

    private final ObjectMapper objectMapper;
    private final ProjectTriageEventUseCase projectEvent;

    public TriageEventProjector(ObjectMapper objectMapper, ProjectTriageEventUseCase projectEvent) {
        this.objectMapper = objectMapper;
        this.projectEvent = projectEvent;
    }

    public Mono<Void> project(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, TriageEventMessage.class))
                .flatMap(message -> {
                    logger.info(
                            "Triage event received",
                            StructuredArguments.keyValue("correlationId", message.correlationId()),
                            StructuredArguments.keyValue("eventType", message.eventType()));
                    return projectEvent.project(message.toDomain())
                            .contextWrite(context -> message.correlationId() == null
                                    ? context
                                    : context.put(CorrelationIdWebFilter.CONTEXT_KEY, message.correlationId()))
                            .doOnError(error -> logger.error(
                                    "Triage event projection failed",
                                    StructuredArguments.keyValue("correlationId", message.correlationId()),
                                    error));
                });
    }
}
