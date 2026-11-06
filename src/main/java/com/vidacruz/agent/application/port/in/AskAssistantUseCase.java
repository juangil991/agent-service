package com.vidacruz.agent.application.port.in;

import com.vidacruz.agent.domain.model.AssistantAnswer;
import reactor.core.publisher.Mono;

public interface AskAssistantUseCase {

    Mono<AssistantAnswer> ask(String pregunta);
}

