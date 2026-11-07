package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.domain.model.AnswerVerification;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import reactor.core.publisher.Mono;

import java.util.List;

public class StubLlmAdapter implements LlmPort {

    @Override
    public Mono<String> generateAnswer(String question, List<ProtocolChunk> evidence, String language) {
        ProtocolChunk first = evidence.get(0);
        String response = "Según %s (%s), %s".formatted(
                first.protocolo(),
                first.seccion(),
                first.contenido());
        return Mono.just(response);
    }

    @Override
    public Mono<AnswerVerification> verifyAnswer(
            String question,
            String answer,
            List<ProtocolChunk> evidence) {
        boolean matchesEvidence = evidence.stream()
                .anyMatch(chunk -> answer.equals("Según %s (%s), %s".formatted(
                        chunk.protocolo(),
                        chunk.seccion(),
                        chunk.contenido())));
        return Mono.just(new AnswerVerification(List.of(
                new AnswerVerification.ClaimAssessment(answer, matchesEvidence))));
    }
}

