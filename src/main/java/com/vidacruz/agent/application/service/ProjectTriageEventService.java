package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.port.in.ProjectTriageEventUseCase;
import com.vidacruz.agent.application.port.out.UrgencyProjectionPort;
import com.vidacruz.agent.domain.model.TriageEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Service
public class ProjectTriageEventService implements ProjectTriageEventUseCase {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "paciente_registrado",
            "paciente_en_consulta",
            "paciente_alta",
            "paciente_escalado");

    private final UrgencyProjectionPort projection;

    public ProjectTriageEventService(UrgencyProjectionPort projection) {
        this.projection = projection;
    }

    @Override
    public Mono<Void> project(TriageEvent event) {
        return Mono.defer(() -> {
            if (event == null
                    || event.eventId() == null
                    || event.eventType() == null
                    || event.patientId() == null
                    || event.triageLevel() == null
                    || event.status() == null
                    || !SUPPORTED_EVENTS.contains(event.eventType())) {
                return Mono.error(new IllegalArgumentException("Evento de triaje inválido o no soportado"));
            }
            return projection.apply(event);
        });
    }
}
