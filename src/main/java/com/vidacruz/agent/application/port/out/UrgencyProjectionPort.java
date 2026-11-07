package com.vidacruz.agent.application.port.out;

import com.vidacruz.agent.domain.model.TriageEvent;
import com.vidacruz.agent.domain.model.TriageLevel;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface UrgencyProjectionPort {

    Mono<Void> apply(TriageEvent event);

    Mono<Map<TriageLevel, Long>> findActiveCounts();
}
