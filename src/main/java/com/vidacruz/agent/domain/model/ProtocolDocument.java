package com.vidacruz.agent.domain.model;

public record ProtocolDocument(
        String documentId,
        String protocolId,
        String title,
        String version,
        String source,
        String markdown) {
}
