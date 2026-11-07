package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.exception.RagUnavailableException;
import com.vidacruz.agent.application.port.out.ProtocolEmbeddingPort;
import com.vidacruz.agent.application.port.out.ProtocolRetrievalPort;
import com.vidacruz.agent.application.port.out.ProtocolVectorStorePort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class ProtocolRetrievalService implements ProtocolRetrievalPort {

    private final ProtocolEmbeddingPort embeddings;
    private final ProtocolVectorStorePort vectorStore;
    private final int candidateLimit;
    private final int rankConstant;
    private final double maxCosineDistance;
    private final double minimumRrfScore;

    public ProtocolRetrievalService(
            ProtocolEmbeddingPort embeddings,
            ProtocolVectorStorePort vectorStore,
            @Value("${app.assistant.retrieval.candidate-limit:20}") int candidateLimit,
            @Value("${app.assistant.retrieval.rrf-k:60}") int rankConstant,
            @Value("${app.assistant.retrieval.max-cosine-distance:0.55}") double maxCosineDistance,
            @Value("${app.assistant.retrieval.minimum-rrf-score:0.015}") double minimumRrfScore) {
        if (candidateLimit < 1 || rankConstant < 1 || maxCosineDistance < 0 || maxCosineDistance > 2
                || !Double.isFinite(minimumRrfScore) || minimumRrfScore < 0 || minimumRrfScore > 2) {
            throw new IllegalArgumentException("La configuración de recuperación RAG no es válida");
        }
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
        this.candidateLimit = candidateLimit;
        this.rankConstant = rankConstant;
        this.maxCosineDistance = maxCosineDistance;
        this.minimumRrfScore = minimumRrfScore;
    }

    @Override
    public Mono<List<ProtocolChunk>> retrieve(String question, int limit) {
        return embeddings.embedQuery(question)
                .flatMap(queryEmbedding -> vectorStore.search(
                        question,
                        queryEmbedding,
                        candidateLimit,
                        limit,
                        rankConstant,
                        maxCosineDistance))
                .map(chunks -> chunks.stream()
                        .filter(chunk -> chunk.relevanceScore() >= minimumRrfScore)
                        .toList())
                .onErrorMap(error -> error instanceof RagUnavailableException
                        ? error
                        : new RagUnavailableException("No se pudo recuperar evidencia de protocolos", error));
    }
}
