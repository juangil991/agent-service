package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.port.out.ProtocolEmbeddingPort;
import com.vidacruz.agent.application.port.out.ProtocolSourcePort;
import com.vidacruz.agent.application.port.out.ProtocolVectorStorePort;
import com.vidacruz.agent.domain.model.EmbeddedProtocolChunk;
import com.vidacruz.agent.domain.model.ProtocolDocument;
import com.vidacruz.agent.domain.model.ProtocolSectionChunk;
import com.vidacruz.agent.domain.service.StructureBasedChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class ProtocolIngestionService {

    private static final int EMBEDDING_BATCH_SIZE = 64;

    private final List<ProtocolSourcePort> sources;
    private final StructureBasedChunker chunker;
    private final ProtocolEmbeddingPort embeddings;
    private final ProtocolVectorStorePort vectorStore;
    private final String embeddingModel;

    public ProtocolIngestionService(
            List<ProtocolSourcePort> sources,
            StructureBasedChunker chunker,
            ProtocolEmbeddingPort embeddings,
            ProtocolVectorStorePort vectorStore,
            @Value("${app.assistant.embeddings.model:voyage-4}") String embeddingModel) {
        this.sources = sources;
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    public Mono<Void> ingest() {
        return Flux.fromIterable(sources)
                .concatMap(ProtocolSourcePort::loadDocuments)
                .flatMapIterable(documents -> documents)
                .concatMap(this::ingestDocument)
                .then();
    }

    private Mono<Void> ingestDocument(ProtocolDocument document) {
        List<ProtocolSectionChunk> chunks = chunker.chunk(document.markdown(), document.title());
        if (chunks.isEmpty()) {
            return Mono.error(new IllegalArgumentException(
                    "El protocolo no contiene texto indexable: " + document.source()));
        }

        String contentHash = sha256(String.join("\u0000",
                document.protocolId(),
                document.title(),
                document.version(),
                document.source(),
                embeddingModel,
                document.markdown()));
        return vectorStore.isDocumentCurrent(document.documentId(), contentHash)
                .flatMap(isCurrent -> {
                    if (isCurrent) {
                        return Mono.empty();
                    }
                    return embedInBatches(chunks)
                            .flatMap(vectors -> {
                                if (vectors.size() != chunks.size()) {
                                    return Mono.error(new IllegalStateException(
                                            "Voyage AI devolvió una cantidad de embeddings inesperada"));
                                }
                                List<EmbeddedProtocolChunk> embeddedChunks = chunks.stream()
                                        .map(chunk -> new EmbeddedProtocolChunk(
                                                document.documentId() + ":" + chunk.chunkIndex(),
                                                document.documentId(),
                                                document.protocolId(),
                                                chunk.section(),
                                                chunk.chunkIndex(),
                                                chunk.content(),
                                                vectors.get(chunk.chunkIndex())))
                                        .toList();
                                return vectorStore.replaceDocument(document, contentHash, embeddedChunks);
                            });
                });
    }

    private Mono<List<List<Double>>> embedInBatches(List<ProtocolSectionChunk> chunks) {
        return Flux.fromIterable(chunks)
                .buffer(EMBEDDING_BATCH_SIZE)
                .concatMap(batch -> embeddings.embedDocuments(
                        batch.stream().map(ProtocolSectionChunk::content).toList()))
                .flatMapIterable(vectors -> vectors)
                .collectList();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }
}
