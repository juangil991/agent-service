package com.vidacruz.agent.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vidacruz.agent.domain.model.PatientStatus;
import com.vidacruz.agent.domain.model.TriageEvent;
import com.vidacruz.agent.domain.model.TriageLevel;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
record TriageEventMessage(
        UUID eventId,
        String eventType,
        String correlationId,
        UUID patientId,
        Payload payload) {

    TriageEvent toDomain() {
        return new TriageEvent(
                eventId,
                eventType,
                patientId,
                payload == null ? null : payload.triageLevel(),
                payload == null ? null : payload.status());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(TriageLevel triageLevel, PatientStatus status) {
    }
}
