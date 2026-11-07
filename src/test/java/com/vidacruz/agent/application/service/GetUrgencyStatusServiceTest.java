package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.port.out.UrgencyProjectionPort;
import com.vidacruz.agent.domain.model.TriageLevel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetUrgencyStatusServiceTest {

    private final UrgencyProjectionPort projection = mock(UrgencyProjectionPort.class);
    private final GetUrgencyStatusService service = new GetUrgencyStatusService(projection);

    @Test
    void includesEveryTriageLevelAndDefaultsMissingCountsToZero() {
        when(projection.findActiveCounts()).thenReturn(Mono.just(Map.of(TriageLevel.CRITICO, 2L)));

        StepVerifier.create(service.getActiveCounts())
                .assertNext(counts -> {
                    assertThat(counts).containsEntry(TriageLevel.CRITICO, 2L);
                    assertThat(counts).containsEntry(TriageLevel.URGENTE, 0L);
                    assertThat(counts).containsEntry(TriageLevel.URGENTE_2, 0L);
                    assertThat(counts).containsEntry(TriageLevel.NO_URGENTE, 0L);
                    assertThat(counts).containsEntry(TriageLevel.NO_URGENTE_2, 0L);
                })
                .verifyComplete();
    }
}
