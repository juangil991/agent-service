package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.in.AskAssistantUseCase;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.application.port.out.ProtocolCorpusPort;
import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.model.SourceCitation;
import com.vidacruz.agent.domain.service.ProtocolRetriever;
import reactor.core.publisher.Mono;

import java.util.List;

public class AssistantQueryService implements AskAssistantUseCase {

    static final String NO_PROTOCOL_MESSAGE = "No existe un protocolo disponible para la pregunta.";

    private final LlmPort llm;
    private final ProtocolCorpusPort corpus;
    private final ProtocolRetriever retriever;
    private final String language;

    public AssistantQueryService(
            LlmPort llm,
            ProtocolCorpusPort corpus,
            ProtocolRetriever retriever,
            String language) {
        this.llm = llm;
        this.corpus = corpus;
        this.retriever = retriever;
        this.language = language;
    }

    @Override
public Mono<AssistantAnswer> ask(String pregunta) {
        String question = pregunta == null ? "" : pregunta.trim();
        if (question.isEmpty()) {
            return Mono.error(new IllegalArgumentException("pregunta es obligatoria"));
        }

        List<ProtocolChunk> evidence = retriever.retrieve(question, corpus.allChunks(), 3);
        if (evidence.isEmpty()) {
            return Mono.just(new AssistantAnswer(NO_PROTOCOL_MESSAGE, List.of()));
        }

        List<SourceCitation> fuentes = evidence.stream()
                .map(chunk -> new SourceCitation(chunk.protocolo(), chunk.seccion()))
                .distinct()
                .toList();

        return llm.generateAnswer(question, evidence, language)
                .map(answer -> new AssistantAnswer(cleanAnswer(answer), fuentes))
                .onErrorMap(error -> error instanceof LlmUnavailableException
                        ? error
                        : new LlmUnavailableException("El proveedor de IA no está disponible", error));
    }

    private String cleanAnswer(String answer) {
        String normalized = answer == null ? "" : answer.trim();
        if (normalized.isEmpty()) {
            throw new LlmUnavailableException("El proveedor de IA devolvió una respuesta vacía");
        }
        return normalized;
    }
}

