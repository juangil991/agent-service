package com.vidacruz.agent.domain.model;

public record ProtocolChunk(
        String chunkId,
        String protocolo,
        String titulo,
        String seccion,
        String contenido,
        double relevanceScore) {
}

