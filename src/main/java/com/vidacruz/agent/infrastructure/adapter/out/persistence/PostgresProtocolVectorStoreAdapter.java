package com.vidacruz.agent.infrastructure.adapter.out.persistence;

import com.vidacruz.agent.application.port.out.ProtocolVectorStorePort;
import com.vidacruz.agent.domain.model.EmbeddedProtocolChunk;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.model.ProtocolDocument;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PostgresProtocolVectorStoreAdapter implements ProtocolVectorStorePort {

    private static final String UPSERT_DOCUMENT = """
            INSERT INTO protocol_document (document_id, protocol_id, title, version, source, content_hash)
            VALUES (:documentId, :protocolId, :title, :version, :source, :contentHash)
            ON CONFLICT (document_id) DO UPDATE SET
                protocol_id = EXCLUDED.protocol_id,
                title = EXCLUDED.title,
                version = EXCLUDED.version,
                source = EXCLUDED.source,
                content_hash = EXCLUDED.content_hash,
                updated_at = CURRENT_TIMESTAMP
            """;

    private static final String HYBRID_SEARCH = """
            WITH text_hits AS (
                SELECT c.chunk_id,
                       RANK() OVER (ORDER BY pdb.score(c.chunk_id) DESC, c.chunk_id) AS position
                FROM protocol_chunk c
                JOIN protocol_document d ON d.document_id = c.document_id
                WHERE c.content ||| :question
                   OR c.section ||| :question
                   OR c.protocol_id ||| :question
                ORDER BY pdb.score(c.chunk_id) DESC, c.chunk_id
                LIMIT :candidateLimit
            ),
            vector_candidates AS (
                SELECT c.chunk_id,
                       c.embedding <=> CAST(:embedding AS vector(1024)) AS distance
                FROM protocol_chunk c
                WHERE c.chunk_id @@@ pdb.all()
                ORDER BY c.embedding <=> CAST(:embedding AS vector(1024)), c.chunk_id
                LIMIT :candidateLimit
            ),
            vector_hits AS (
                SELECT chunk_id,
                       RANK() OVER (ORDER BY distance, chunk_id) AS position
                FROM vector_candidates
                WHERE distance <= :maxCosineDistance
            ),
            weighted_hits AS (
                SELECT chunk_id, 1.0 / (:rankConstant + position) AS weight
                FROM text_hits
                UNION ALL
                SELECT chunk_id, 0.7 / (:rankConstant + position) AS weight
                FROM vector_hits
            ),
            fused_hits AS (
                SELECT chunk_id, SUM(weight) AS score
                FROM weighted_hits
                GROUP BY chunk_id
            )
            SELECT c.chunk_id, c.protocol_id, d.title, c.section, c.content, f.score AS relevance_score
            FROM fused_hits f
            JOIN protocol_chunk c ON c.chunk_id = f.chunk_id
            JOIN protocol_document d ON d.document_id = c.document_id
            ORDER BY f.score DESC, c.chunk_id
            LIMIT :resultLimit
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public PostgresProtocolVectorStoreAdapter(
            DatabaseClient databaseClient,
            TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<Boolean> isDocumentCurrent(String documentId, String contentHash) {
        return databaseClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM protocol_document
                            WHERE document_id = :documentId AND content_hash = :contentHash
                        ) AS current
                        """)
                .bind("documentId", documentId)
                .bind("contentHash", contentHash)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("current", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> replaceDocument(
            ProtocolDocument document,
            String contentHash,
            List<EmbeddedProtocolChunk> chunks) {
        Mono<Void> replacement = databaseClient.sql(UPSERT_DOCUMENT)
                .bind("documentId", document.documentId())
                .bind("protocolId", document.protocolId())
                .bind("title", document.title())
                .bind("version", document.version())
                .bind("source", document.source())
                .bind("contentHash", contentHash)
                .fetch()
                .rowsUpdated()
                .then(databaseClient.sql("DELETE FROM protocol_chunk WHERE document_id = :documentId")
                        .bind("documentId", document.documentId())
                        .fetch()
                        .rowsUpdated())
                .thenMany(Flux.fromIterable(chunks).concatMap(this::insertChunk))
                .then();
        return transactionalOperator.transactional(replacement);
    }

    @Override
    public Mono<List<ProtocolChunk>> search(
            String question,
            List<Double> queryEmbedding,
            int candidateLimit,
            int resultLimit,
            int rankConstant,
            double maxCosineDistance) {
        return databaseClient.sql(HYBRID_SEARCH)
                .bind("question", question)
                .bind("embedding", vectorLiteral(queryEmbedding))
                .bind("candidateLimit", candidateLimit)
                .bind("resultLimit", resultLimit)
                .bind("rankConstant", rankConstant)
                .bind("maxCosineDistance", maxCosineDistance)
                .map((row, metadata) -> new ProtocolChunk(
                        row.get("chunk_id", String.class),
                        row.get("protocol_id", String.class),
                        row.get("title", String.class),
                        row.get("section", String.class),
                        row.get("content", String.class),
                        row.get("relevance_score", Double.class)))
                .all()
                .collectList();
    }

    private Mono<Long> insertChunk(EmbeddedProtocolChunk chunk) {
        return databaseClient.sql("""
                        INSERT INTO protocol_chunk
                            (chunk_id, document_id, protocol_id, section, chunk_index, content, embedding)
                        VALUES
                            (:chunkId, :documentId, :protocolId, :section, :chunkIndex, :content,
                             CAST(:embedding AS vector(1024)))
                        """)
                .bind("chunkId", chunk.chunkId())
                .bind("documentId", chunk.documentId())
                .bind("protocolId", chunk.protocolId())
                .bind("section", chunk.section())
                .bind("chunkIndex", chunk.chunkIndex())
                .bind("content", chunk.content())
                .bind("embedding", vectorLiteral(chunk.embedding()))
                .fetch()
                .rowsUpdated();
    }

    private String vectorLiteral(List<Double> vector) {
        return vector.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }
}
