package com.vidacruz.agent.application.port.in;

import com.vidacruz.agent.domain.model.TriageEvent;
import reactor.core.publisher.Mono;

public interface ProjectTriageEventUseCase {

    Mono<Void> project(TriageEvent event);
}
