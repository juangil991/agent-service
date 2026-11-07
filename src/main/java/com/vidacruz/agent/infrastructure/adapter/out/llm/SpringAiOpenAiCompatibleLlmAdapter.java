package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

public class SpringAiOpenAiCompatibleLlmAdapter implements LlmPort {

    private final ChatClient chatClient;
    private final String providerName;
    private final String apiKey;

    public SpringAiOpenAiCompatibleLlmAdapter(
            ChatClient chatClient,
            String providerName,
            String apiKey) {
        this.chatClient = chatClient;
        this.providerName = providerName;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public Mono<String> selectEvidence(String question, List<ProtocolChunk> evidence, String language) {
        if (apiKey.isBlank()) {
            return Mono.error(new LlmUnavailableException(
                    "El proveedor de IA no está configurado: falta API key para %s".formatted(providerName)));
        }

        return complete(buildAnswerPrompt(question, evidence, language))
                .map(this::validateAnswer)
                .onErrorMap(error -> mapProviderError(error, "obtener respuesta"));
    }

    private Mono<String> complete(String prompt) {
        return Mono.fromCallable(() -> chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String buildAnswerPrompt(String question, List<ProtocolChunk> evidence, String language) {
        String context = evidence.stream()
                .map(chunk -> """
                        [chunkId=%s | protocolo=%s | titulo=%s | seccion=%s]
                        %s
                        """.formatted(
                        chunk.chunkId(),
                        chunk.protocolo(),
                        chunk.titulo(),
                        chunk.seccion(),
                        chunk.contenido()))
                .collect(Collectors.joining("\n"));

        return """
                Selecciona únicamente texto literal del CONTEXTO que responda a la pregunta.
                No redactes, parafrasees, traduzcas, completes ni agregues contenido clínico.
                Devuelve exclusivamente JSON con esta forma:
                {"extractos":[{"chunkId":"ID_DEL_FRAGMENTO","texto":"SUBCADENA_LITERAL_DEL_CONTEXTO"}]}
                Cada extracto debe ser una subcadena exacta del contenido del fragmento indicado.
                Usa solo chunkId incluidos en el CONTEXTO; no inventes identificadores.
                Si ningún extracto responde a la pregunta, devuelve exactamente {"extractos":[]}.
                Conserva el idioma original del texto citado (%s).

                PREGUNTA:
                %s

                CONTEXTO:
                %s
                """.formatted(language, question, context);
    }

    private String validateAnswer(String answer) {
        String normalized = answer == null ? "" : answer.trim();
        if (normalized.isEmpty()) {
            throw new LlmUnavailableException("El proveedor de IA devolvió una respuesta vacía");
        }
        return normalized;
    }

    private RuntimeException mapProviderError(Throwable error, String operation) {
        return error instanceof LlmUnavailableException
                ? (LlmUnavailableException) error
                : new LlmUnavailableException("No se pudo %s con el proveedor de IA (%s)"
                        .formatted(operation, providerName), error);
    }
}
