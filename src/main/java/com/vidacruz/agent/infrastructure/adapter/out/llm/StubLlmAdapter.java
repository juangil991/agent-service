package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.vidacruz.agent.application.port.out.LlmPort;
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
}

