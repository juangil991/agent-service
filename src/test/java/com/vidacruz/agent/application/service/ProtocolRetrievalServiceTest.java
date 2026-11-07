package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.exception.RagUnavailableException;
import com.vidacruz.agent.application.port.out.ProtocolEmbeddingPort;
import com.vidacruz.agent.application.port.out.ProtocolVectorStorePort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtocolRetrievalServiceTest {

    private final ProtocolEmbeddingPort embeddings = mock(ProtocolEmbeddingPort.class);
    private final ProtocolVectorStorePort vectorStore = mock(ProtocolVectorStorePort.class);
    private final ProtocolRetrievalService service = new ProtocolRetrievalService(
            embeddings, vectorStore, 20, 60, 0.55, 0.015);

    @Test
    void embedsQuestionAndReturnsHybridSearchResults() {
        ProtocolChunk evidence = new ProtocolChunk(
                "chunk-sepsis",
                "PROTO-002",
                "Sepsis, Identificación Temprana",
                "Acciones iniciales",
                "Antibiótico en la primera hora.",
                0.02);
        when(embeddings.embedQuery("¿Cuándo se trata la sepsis?"))
                .thenReturn(Mono.just(List.of(0.2, 0.3)));
        when(vectorStore.search(anyString(), any(), anyInt(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(Mono.just(List.of(evidence)));

        StepVerifier.create(service.retrieve("¿Cuándo se trata la sepsis?", 3))
                .assertNext(result -> assertThat(result).containsExactly(evidence))
                .verifyComplete();

        verify(vectorStore).search("¿Cuándo se trata la sepsis?", List.of(0.2, 0.3), 20, 3, 60, 0.55);
    }

    @Test
    void discardsResultsBelowTheMinimumHybridRelevanceScore() {
        ProtocolChunk weakEvidence = new ProtocolChunk(
                "chunk-weak",
                "PROTO-002",
                "Sepsis",
                "Acciones iniciales",
                "Antibiótico en la primera hora.",
                0.014);
        when(embeddings.embedQuery("¿Cuándo se trata la sepsis?"))
                .thenReturn(Mono.just(List.of(0.2, 0.3)));
        when(vectorStore.search(anyString(), any(), anyInt(), anyInt(), anyInt(), anyDouble()))
                .thenReturn(Mono.just(List.of(weakEvidence)));

        StepVerifier.create(service.retrieve("¿Cuándo se trata la sepsis?", 3))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();
    }

    @Test
    void wrapsEmbeddingFailureAsAssistantDependencyUnavailable() {
        when(embeddings.embedQuery(anyString())).thenReturn(Mono.error(new RuntimeException("network failure")));

        StepVerifier.create(service.retrieve("consulta", 3))
                .expectError(RagUnavailableException.class)
                .verify();
    }
}
