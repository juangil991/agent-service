package com.vidacruz.agent.application.port.out;

import reactor.core.publisher.Mono;

import java.util.List;

public interface ProtocolEmbeddingPort {

    Mono<List<List<Double>>> embedDocuments(List<String> texts);

    Mono<List<Double>> embedQuery(String question);
}
