package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.port.out.UrgencyProjectionPort;
import com.vidacruz.agent.domain.model.PatientStatus;
import com.vidacruz.agent.domain.model.TriageEvent;
import com.vidacruz.agent.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectTriageEventServiceTest {

    private final UrgencyProjectionPort projection = mock(UrgencyProjectionPort.class);
    private final ProjectTriageEventService service = new ProjectTriageEventService(projection);

    @Test
    void projectsSupportedPatientEvents() {
        TriageEvent event = new TriageEvent(
                UUID.randomUUID(),
                "paciente_en_consulta",
                UUID.randomUUID(),
                TriageLevel.URGENTE,
                PatientStatus.EN_CONSULTA);
        when(projection.apply(event)).thenReturn(Mono.empty());

        StepVerifier.create(service.project(event)).verifyComplete();

        verify(projection).apply(event);
    }

    @Test
    void rejectsUnsupportedEventsWithoutChangingProjection() {
        TriageEvent event = new TriageEvent(
                UUID.randomUUID(),
                "evento_desconocido",
                UUID.randomUUID(),
                TriageLevel.URGENTE,
                PatientStatus.REGISTRADO);

        StepVerifier.create(service.project(event))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(projection);
    }

    @Test
    void rejectsEscalationEventsWithMissingSnapshot() {
        TriageEvent event = new TriageEvent(
                UUID.randomUUID(),
                "paciente_escalado",
                UUID.randomUUID(),
                TriageLevel.CRITICO,
                null);

        StepVerifier.create(service.project(event))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(projection);
    }
}
