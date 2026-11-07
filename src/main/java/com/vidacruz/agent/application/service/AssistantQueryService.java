package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.in.AskAssistantUseCase;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.application.port.out.ProtocolRetrievalPort;
import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.model.SourceCitation;
import reactor.core.publisher.Mono;

import java.util.List;

public class AssistantQueryService implements AskAssistantUseCase {

    static final String NO_PROTOCOL_MESSAGE = "No existe un protocolo disponible para la pregunta.";

    private final LlmPort llm;
    private final ProtocolRetrievalPort retriever;
    private final String language;
    private final int resultLimit;

    public AssistantQueryService(
            LlmPort llm,
            ProtocolRetrievalPort retriever,
            String language,
            int resultLimit) {
        if (resultLimit < 1) {
            throw new IllegalArgumentException("resultLimit debe ser positivo");
        }
        this.llm = llm;
        this.retriever = retriever;
        this.language = language;
        this.resultLimit = resultLimit;
    }

    @Override
    public Mono<AssistantAnswer> ask(String pregunta) {
        String question = pregunta == null ? "" : pregunta.trim();
        if (question.isEmpty()) {
            return Mono.error(new IllegalArgumentException("pregunta es obligatoria"));
        }

        return Mono.defer(() -> retriever.retrieve(question, resultLimit))
                .flatMap(evidence -> {
                    if (evidence.isEmpty()) {
                        return Mono.just(new AssistantAnswer(NO_PROTOCOL_MESSAGE, List.of()));
                    }

                    List<SourceCitation> fuentes = evidence.stream()
                            .map(chunk -> new SourceCitation(chunk.protocolo(), chunk.seccion()))
                            .distinct()
                            .toList();

                    return llm.generateAnswer(question, evidence, language)
                            .switchIfEmpty(Mono.error(new LlmUnavailableException(
                                    "El proveedor de IA devolvió una respuesta vacía")))
                            .flatMap(answer -> {
                                String normalizedAnswer = cleanAnswer(answer);
                                if (NO_PROTOCOL_MESSAGE.equals(normalizedAnswer)) {
                                    return Mono.just(new AssistantAnswer(NO_PROTOCOL_MESSAGE, List.of()));
                                }
                                return llm.verifyAnswer(question, normalizedAnswer, evidence)
                                        .switchIfEmpty(Mono.error(new LlmUnavailableException(
                                                "El proveedor de IA no devolvió resultado de verificación")))
                                        .map(verification -> verification.allClaimsSupported()
                                                ? new AssistantAnswer(normalizedAnswer, fuentes)
                                                : new AssistantAnswer(NO_PROTOCOL_MESSAGE, List.of()))
                                        .onErrorMap(error -> error instanceof LlmUnavailableException
                                                ? error
                                                : new LlmUnavailableException(
                                                        "No se pudo verificar el soporte de la respuesta", error));
                            })
                            .onErrorMap(error -> error instanceof LlmUnavailableException
                                    ? error
                                    : new LlmUnavailableException("El proveedor de IA no está disponible", error));
                });
    }

    private String cleanAnswer(String answer) {
        String normalized = answer == null ? "" : answer.trim();
        if (normalized.isEmpty()) {
            throw new LlmUnavailableException("El proveedor de IA devolvió una respuesta vacía");
        }
        return normalized;
    }
}

