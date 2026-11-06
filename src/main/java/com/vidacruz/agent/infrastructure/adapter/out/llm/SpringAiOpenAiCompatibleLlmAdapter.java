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

    public SpringAiOpenAiCompatibleLlmAdapter(ChatClient chatClient, String providerName, String apiKey) {
        this.chatClient = chatClient;
        this.providerName = providerName;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public Mono<String> generateAnswer(String question, List<ProtocolChunk> evidence, String language) {
        if (apiKey.isBlank()) {
            return Mono.error(new LlmUnavailableException(
                    "El proveedor de IA no está configurado: falta API key para %s".formatted(providerName)));
        }

        String prompt = buildPrompt(question, evidence, language);
        return Mono.fromCallable(() -> chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content())
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::validateAnswer)
                .onErrorMap(error -> error instanceof LlmUnavailableException
                        ? error
                        : new LlmUnavailableException(
                                "No se pudo obtener respuesta del proveedor de IA (%s)".formatted(providerName),
                                error));
    }

    private String buildPrompt(String question, List<ProtocolChunk> evidence, String language) {
        String context = evidence.stream()
                .map(chunk -> String.format(
                        "[%s | %s] %s",
                        chunk.protocolo(),
                        chunk.seccion(),
                        chunk.contenido()))
                .collect(Collectors.joining("\n"));

        return """
                Eres un asistente clínico estricto.
                Responde únicamente con base en el CONTEXTO.
                No inventes información ni agregues recomendaciones no presentes.
                Responde en idioma: %s.
                Si el contexto no alcanza, responde exactamente: "No existe un protocolo disponible para la pregunta."

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
}
