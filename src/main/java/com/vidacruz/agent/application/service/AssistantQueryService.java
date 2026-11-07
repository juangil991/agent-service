package com.vidacruz.agent.application.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.in.AskAssistantUseCase;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.application.port.out.ProtocolRetrievalPort;
import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.model.SourceCitation;
import com.vidacruz.agent.domain.service.ProtocolEvidenceRelevance;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AssistantQueryService implements AskAssistantUseCase {

    static final String NO_PROTOCOL_MESSAGE = "No existe un protocolo disponible para la pregunta.";

    private final LlmPort llm;
    private final ProtocolRetrievalPort retriever;
    private final String language;
    private final int resultLimit;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final ProtocolEvidenceRelevance evidenceRelevance;

    public AssistantQueryService(
            LlmPort llm,
            ProtocolRetrievalPort retriever,
            String language,
            int resultLimit,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            ProtocolEvidenceRelevance evidenceRelevance) {
        if (resultLimit < 1) {
            throw new IllegalArgumentException("resultLimit debe ser positivo");
        }
        this.llm = llm;
        this.retriever = retriever;
        this.language = language;
        this.resultLimit = resultLimit;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.evidenceRelevance = evidenceRelevance;
    }

    @Override
    public Mono<AssistantAnswer> ask(String pregunta) {
        String question = pregunta == null ? "" : pregunta.trim();
        if (question.isEmpty()) {
            return Mono.error(new IllegalArgumentException("pregunta es obligatoria"));
        }

        return Mono.defer(() -> retriever.retrieve(question, resultLimit))
                .flatMap(evidence -> {
                    List<ProtocolChunk> relevantEvidence = evidence.stream()
                            .filter(this::hasValidSource)
                            .filter(chunk -> evidenceRelevance.isRelevant(question, chunk))
                            .toList();
                    if (relevantEvidence.isEmpty()) {
                        return Mono.just(new AssistantAnswer(NO_PROTOCOL_MESSAGE, List.of()));
                    }

                    return timed("select_evidence", llm.selectEvidence(question, relevantEvidence, language))
                            .switchIfEmpty(Mono.error(new LlmUnavailableException(
                                    "El proveedor de IA devolvió una selección vacía")))
                            .map(selection -> validateSelection(question, selection, relevantEvidence)
                                    .orElseGet(this::abstention))
                            .onErrorMap(error -> error instanceof LlmUnavailableException
                                    ? error
                                    : new LlmUnavailableException(
                                            "El proveedor de IA no está disponible", error));
                });
    }

    private Optional<AssistantAnswer> validateSelection(
            String question,
            String selection,
            List<ProtocolChunk> evidence) {
        JsonNode root;
        try (JsonParser parser = objectMapper.createParser(selection)) {
            root = objectMapper.readTree(parser);
            if (parser.nextToken() != null) {
                return Optional.empty();
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
        if (root == null || !root.isObject() || root.size() != 1
                || !root.has("extractos") || !root.get("extractos").isArray()) {
            return Optional.empty();
        }

        JsonNode excerpts = root.get("extractos");
        if (excerpts.isEmpty() || excerpts.size() > evidence.size()) {
            return Optional.empty();
        }

        List<String> validatedQuotes = new ArrayList<>();
        List<SourceCitation> citations = new ArrayList<>();
        Set<String> selectedChunkIds = new HashSet<>();
        for (JsonNode excerpt : excerpts) {
            if (!excerpt.isObject() || excerpt.size() != 2
                    || !excerpt.hasNonNull("chunkId") || !excerpt.get("chunkId").isTextual()
                    || !excerpt.hasNonNull("texto") || !excerpt.get("texto").isTextual()) {
                return Optional.empty();
            }

            String chunkId = excerpt.get("chunkId").textValue();
            String quote = excerpt.get("texto").textValue();
            if (chunkId.isBlank() || quote.isBlank() || !selectedChunkIds.add(chunkId)) {
                return Optional.empty();
            }

            ProtocolChunk source = evidence.stream()
                    .filter(chunk -> chunk.chunkId().equals(chunkId))
                    .findFirst()
                    .orElse(null);
            if (source == null || !source.contenido().contains(quote)
                    || !evidenceRelevance.isRelevant(question, source)) {
                return Optional.empty();
            }

            validatedQuotes.add(quote);
            citations.add(new SourceCitation(source.protocolo(), source.seccion()));
        }

        return Optional.of(new AssistantAnswer(
                String.join("\n", validatedQuotes),
                citations.stream().distinct().toList()));
    }

    private boolean hasValidSource(ProtocolChunk chunk) {
        return chunk.chunkId() != null && !chunk.chunkId().isBlank()
                && chunk.protocolo() != null && !chunk.protocolo().isBlank()
                && chunk.titulo() != null && !chunk.titulo().isBlank()
                && chunk.seccion() != null && !chunk.seccion().isBlank()
                && chunk.contenido() != null && !chunk.contenido().isBlank();
    }

    private AssistantAnswer abstention() {
        return new AssistantAnswer(NO_PROTOCOL_MESSAGE, List.of());
    }

    private <T> Mono<T> timed(String operation, Mono<T> result) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            return result.doFinally(signal -> sample.stop(Timer.builder("vidacruz.llm.latency")
                    .description("Latency of LLM evidence selection")
                    .tag("operation", operation)
                    .tag("outcome", signal == reactor.core.publisher.SignalType.ON_ERROR ? "error" : "success")
                    .register(meterRegistry)));
        });
    }
}

