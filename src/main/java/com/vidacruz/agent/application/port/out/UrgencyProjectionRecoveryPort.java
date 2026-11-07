package com.vidacruz.agent.application.port.out;

import reactor.core.publisher.Mono;

public interface UrgencyProjectionRecoveryPort {

    Mono<Boolean> isComplete();

    Mono<Void> clear();

    Mono<Void> markComplete();
}
