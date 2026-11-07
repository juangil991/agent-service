package com.vidacruz.agent.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.agent.domain.model.PatientStatus;
import com.vidacruz.agent.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TriageEventMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsTriageSnapshotAndIgnoresOtherContractFields() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "paciente_registrado",
                  "eventVersion": 1,
                  "occurredAt": "2026-11-06T10:00:00Z",
                  "patientId": "%s",
                  "correlationId": "correlation-id",
                  "payload": {
                    "triageLevel": "NO_URGENTE",
                    "status": "REGISTRADO",
                    "slaMinutes": 60,
                    "escalado": false
                  }
                }
                """.formatted(eventId, patientId);

        TriageEventMessage message = objectMapper.readValue(json, TriageEventMessage.class);

        assertThat(message.toDomain().eventId()).isEqualTo(eventId);
        assertThat(message.correlationId()).isEqualTo("correlation-id");
        assertThat(message.toDomain().patientId()).isEqualTo(patientId);
        assertThat(message.toDomain().triageLevel()).isEqualTo(TriageLevel.NO_URGENTE);
        assertThat(message.toDomain().status()).isEqualTo(PatientStatus.REGISTRADO);
    }
}
