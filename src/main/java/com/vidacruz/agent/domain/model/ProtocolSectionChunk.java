package com.vidacruz.agent.domain.model;

public record ProtocolSectionChunk(
        int chunkIndex,
        String section,
        String content) {
}
