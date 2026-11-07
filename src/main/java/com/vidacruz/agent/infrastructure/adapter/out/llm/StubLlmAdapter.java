package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import reactor.core.publisher.Mono;

import java.util.List;

public class StubLlmAdapter implements LlmPort {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<String> selectEvidence(String question, List<ProtocolChunk> evidence, String language) {
        ProtocolChunk first = evidence.get(0);
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode excerpts = response.putArray("extractos");
        ObjectNode excerpt = excerpts.addObject();
        excerpt.put("chunkId", first.chunkId());
        excerpt.put("texto", first.contenido());
        try {
            return Mono.just(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException exception) {
            return Mono.error(new IllegalStateException("No se pudo serializar la selección de evidencia", exception));
        }
    }

}

