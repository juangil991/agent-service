package com.vidacruz.agent.infrastructure.config;

import com.vidacruz.agent.application.port.in.AskAssistantUseCase;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.application.port.out.ProtocolRetrievalPort;
import com.vidacruz.agent.application.service.AssistantQueryService;
import com.vidacruz.agent.domain.service.StructureBasedChunker;
import com.vidacruz.agent.infrastructure.adapter.out.llm.SpringAiOpenAiCompatibleLlmAdapter;
import com.vidacruz.agent.infrastructure.adapter.out.llm.StubLlmAdapter;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Configuration
public class AssistantConfiguration {

    @Bean
    StructureBasedChunker structureBasedChunker(
            @Value("${app.assistant.protocols.chunk-max-characters:3000}") int maxCharacters) {
        return new StructureBasedChunker(maxCharacters);
    }

    @Bean
    LlmPort llmPort(
            @Value("${app.assistant.llm.provider:google-ai-studio}") String provider,
            @Value("${app.assistant.llm.providers.google-ai-studio.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String googleBaseUrl,
            @Value("${app.assistant.llm.providers.google-ai-studio.completions-path:/v1/chat/completions}") String googleCompletionsPath,
            @Value("${app.assistant.llm.providers.google-ai-studio.model:gemini-2.0-flash}") String googleModel,
            @Value("${app.assistant.llm.providers.google-ai-studio.api-key:}") String googleApiKey,
            @Value("${app.assistant.llm.providers.groqcloud.base-url:https://api.groq.com/openai}") String groqBaseUrl,
            @Value("${app.assistant.llm.providers.groqcloud.completions-path:/v1/chat/completions}") String groqCompletionsPath,
            @Value("${app.assistant.llm.providers.groqcloud.model:llama-3.1-70b-versatile}") String groqModel,
            @Value("${app.assistant.llm.providers.groqcloud.api-key:}") String groqApiKey,
            @Value("${app.assistant.llm.providers.mistral.base-url:https://api.mistral.ai}") String mistralBaseUrl,
            @Value("${app.assistant.llm.providers.mistral.completions-path:/v1/chat/completions}") String mistralCompletionsPath,
            @Value("${app.assistant.llm.providers.mistral.model:mistral-large-latest}") String mistralModel,
            @Value("${app.assistant.llm.providers.mistral.api-key:}") String mistralApiKey,
            @Value("${app.assistant.llm.providers.openai.base-url:https://api.openai.com}") String openAiBaseUrl,
            @Value("${app.assistant.llm.providers.openai.completions-path:/v1/chat/completions}") String openAiCompletionsPath,
            @Value("${app.assistant.llm.providers.openai.model:gpt-4o-mini}") String openAiModel,
            @Value("${app.assistant.llm.providers.openai.api-key:}") String openAiApiKey) {
        if ("stub".equalsIgnoreCase(provider)) {
            return new StubLlmAdapter();
        }

        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        ProviderConfig google = new ProviderConfig(
                "Google AI Studio", googleBaseUrl, googleCompletionsPath, googleModel, googleApiKey);
        ProviderConfig groq = new ProviderConfig(
                "GroqCloud", groqBaseUrl, groqCompletionsPath, groqModel, groqApiKey);
        ProviderConfig mistral = new ProviderConfig(
                "Mistral", mistralBaseUrl, mistralCompletionsPath, mistralModel, mistralApiKey);
        ProviderConfig openAi = new ProviderConfig(
                "OpenAI", openAiBaseUrl, openAiCompletionsPath, openAiModel, openAiApiKey);

        providers.put("google-ai-studio", google);
        providers.put("google", google);
        providers.put("groqcloud", groq);
        providers.put("groq", groq);
        providers.put("mistral", mistral);
        providers.put("openai", openAi);

        ProviderConfig selected = providers.get(normalizeProvider(provider));
        if (selected == null) {
            return unavailablePort("Proveedor de IA no soportado: %s".formatted(provider));
        }
        if (isBlank(selected.apiKey())) {
            return unavailablePort("El proveedor de IA no está configurado: falta API key para %s"
                    .formatted(selected.displayName()));
        }
        if (isBlank(selected.baseUrl()) || isBlank(selected.completionsPath()) || isBlank(selected.model())) {
            return unavailablePort("El proveedor de IA no está configurado correctamente para %s"
                    .formatted(selected.displayName()));
        }

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(selected.baseUrl())
                .apiKey(selected.apiKey())
                .completionsPath(selected.completionsPath())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(selected.model())
                .temperature(0.1)
                .build();
        OpenAiChatModel chatModel = new OpenAiChatModel(
                api,
                options,
                ToolCallingManager.builder().build(),
                RetryTemplate.builder().maxAttempts(1).build(),
                ObservationRegistry.NOOP);

        return new SpringAiOpenAiCompatibleLlmAdapter(
                ChatClient.builder(chatModel).build(),
                selected.displayName(),
                selected.apiKey());
    }

    @Bean
    AskAssistantUseCase askAssistantUseCase(
            LlmPort llm,
            ProtocolRetrievalPort retriever,
            @Value("${app.assistant.language:es}") String language,
            @Value("${app.assistant.retrieval.result-limit:3}") int resultLimit) {
        return new AssistantQueryService(llm, retriever, language, resultLimit);
    }

    private LlmPort unavailablePort(String message) {
        return (question, evidence, language) -> Mono.error(
                new com.vidacruz.agent.application.exception.LlmUnavailableException(message));
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            return "";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ProviderConfig(String displayName, String baseUrl, String completionsPath, String model,
                                  String apiKey) {
    }
}

