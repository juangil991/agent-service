package com.vidacruz.agent.application.port.out;

import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.model.AnswerVerification;
import reactor.core.publisher.Mono;

import java.util.List;

public interface LlmPort {

    Mono<String> generateAnswer(String question, List<ProtocolChunk> evidence, String language);

    Mono<AnswerVerification> verifyAnswer(
            String question,
            String answer,
            List<ProtocolChunk> evidence);
}

