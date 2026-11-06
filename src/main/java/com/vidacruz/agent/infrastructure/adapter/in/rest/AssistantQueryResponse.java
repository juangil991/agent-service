package com.vidacruz.agent.infrastructure.adapter.in.rest;

import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.SourceCitation;

import java.util.List;

public record AssistantQueryResponse(
        String respuesta,
        List<FuenteResponse> fuentes) {

    public static AssistantQueryResponse from(AssistantAnswer answer) {
        return new AssistantQueryResponse(
                answer.respuesta(),
                answer.fuentes().stream()
                        .map(FuenteResponse::from)
                        .toList());
    }

    public record FuenteResponse(String protocolo, String seccion) {
        static FuenteResponse from(SourceCitation citation) {
            return new FuenteResponse(citation.protocolo(), citation.seccion());
        }
    }
}

