package com.vidacruz.agent.application.port.out;

import reactor.core.publisher.Mono;

public interface TriageEventReplayPort {

    Mono<Void> replay();
}
