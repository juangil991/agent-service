package com.vidacruz.agent.application.port.in;

import com.vidacruz.agent.domain.model.TriageLevel;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface GetUrgencyStatusUseCase {

    Mono<Map<TriageLevel, Long>> getActiveCounts();
}
