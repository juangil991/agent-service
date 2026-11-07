package com.vidacruz.agent.application.port.out;

import com.vidacruz.agent.domain.model.ProtocolChunk;
import reactor.core.publisher.Mono;

import java.util.List;

public interface LlmPort {

    Mono<String> generateAnswer(String question, List<ProtocolChunk> evidence, String language);

    Mono<Boolean> evaluateAnswer(
            String question,
            String answer,
            List<ProtocolChunk> evidence);
}

