package com.vidacruz.agent.domain.model;

import java.util.List;

public record EmbeddedProtocolChunk(
        String chunkId,
        String documentId,
        String protocolId,
        String section,
        int chunkIndex,
        String content,
        List<Double> embedding) {
}
