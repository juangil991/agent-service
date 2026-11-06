package com.vidacruz.agent.domain.model;

import java.util.List;

public record AssistantAnswer(
        String respuesta,
        List<SourceCitation> fuentes) {
}

