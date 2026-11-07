package com.vidacruz.agent.application.port.out;

import com.vidacruz.agent.domain.model.EmbeddedProtocolChunk;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.model.ProtocolDocument;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProtocolVectorStorePort {

    Mono<Boolean> isDocumentCurrent(String documentId, String contentHash);

    Mono<Void> replaceDocument(
            ProtocolDocument document,
            String contentHash,
            List<EmbeddedProtocolChunk> chunks);

    Mono<List<ProtocolChunk>> search(
            String question,
            List<Double> queryEmbedding,
            int candidateLimit,
            int resultLimit,
            int rankConstant,
            double maxCosineDistance);
}
