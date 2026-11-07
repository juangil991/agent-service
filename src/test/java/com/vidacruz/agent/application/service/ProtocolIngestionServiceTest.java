package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.port.out.ProtocolEmbeddingPort;
import com.vidacruz.agent.application.port.out.ProtocolSourcePort;
import com.vidacruz.agent.application.port.out.ProtocolVectorStorePort;
import com.vidacruz.agent.domain.model.ProtocolDocument;
import com.vidacruz.agent.domain.service.StructureBasedChunker;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProtocolIngestionServiceTest {

    private final ProtocolSourcePort source = mock(ProtocolSourcePort.class);
    private final ProtocolEmbeddingPort embeddings = mock(ProtocolEmbeddingPort.class);
    private final ProtocolVectorStorePort vectorStore = mock(ProtocolVectorStorePort.class);
    private final ProtocolIngestionService service = new ProtocolIngestionService(
            List.of(source), new StructureBasedChunker(3000), embeddings, vectorStore, "voyage-4");
    private final ProtocolDocument document = new ProtocolDocument(
            "markdown:PROTO-004.md",
            "PROTO-004",
            "Protocolo de dolor",
            "",
            "PROTO-004.md",
            "# Protocolo de dolor\n\n## Evaluación\nEvaluar los síntomas.");

    @Test
    void generatesAndStoresEmbeddingsForNewDocument() {
        when(source.loadDocuments()).thenReturn(Mono.just(List.of(document)));
        when(vectorStore.isDocumentCurrent(anyString(), anyString())).thenReturn(Mono.just(false));
        when(embeddings.embedDocuments(anyList())).thenReturn(Mono.just(List.of(List.of(0.1))));
        when(vectorStore.replaceDocument(any(), anyString(), anyList())).thenReturn(Mono.empty());

        StepVerifier.create(service.ingest()).verifyComplete();

        verify(embeddings).embedDocuments(List.of("Evaluar los síntomas."));
        verify(vectorStore).replaceDocument(any(), anyString(), anyList());
    }

    @Test
    void skipsUnchangedDocumentWithoutCallingEmbeddingProvider() {
        when(source.loadDocuments()).thenReturn(Mono.just(List.of(document)));
        when(vectorStore.isDocumentCurrent(anyString(), anyString())).thenReturn(Mono.just(true));

        StepVerifier.create(service.ingest()).verifyComplete();

        verify(embeddings, never()).embedDocuments(anyList());
        verify(vectorStore, never()).replaceDocument(any(), anyString(), anyList());
    }
}
