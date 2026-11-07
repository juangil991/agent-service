package com.vidacruz.agent.domain.model;

import java.util.UUID;

public record TriageEvent(
        UUID eventId,
        String eventType,
        UUID patientId,
        TriageLevel triageLevel,
        PatientStatus status) {
}
