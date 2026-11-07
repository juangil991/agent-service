package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiOpenAiCompatibleLlmAdapterTest {

    private static final String SELECTION =
            "{\"extractos\":[{\"chunkId\":\"chunk-001\",\"texto\":\"Contenido de respaldo\"}]}";
    private static final ProtocolChunk EVIDENCE = new ProtocolChunk(
            "chunk-001",
            "PROTO-001",
            "Protocolo de prueba",
            "Sección de evaluación",
            "Contenido de respaldo",
            0.02);

    @Test
    void asksTheProviderToSelectLiteralExcerptsAsJson() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn(SELECTION);
        SpringAiOpenAiCompatibleLlmAdapter adapter = new SpringAiOpenAiCompatibleLlmAdapter(
                chatClient,
                "proveedor de prueba",
                "test-key");

        StepVerifier.create(adapter.selectEvidence("pregunta clínica", List.of(EVIDENCE), "es"))
                .expectNext(SELECTION)
                .verifyComplete();

        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(request).user(prompt.capture());
        assertThat(prompt.getValue())
                .contains("Devuelve exclusivamente JSON")
                .contains("subcadena exacta")
                .contains("chunkId=chunk-001")
                .contains("PROTO-001")
                .contains("Contenido de respaldo");
    }

    @Test
    void doesNotCallProviderWithoutAnApiKey() {
        ChatClient chatClient = mock(ChatClient.class);
        SpringAiOpenAiCompatibleLlmAdapter adapter = new SpringAiOpenAiCompatibleLlmAdapter(
                chatClient,
                "proveedor de prueba",
                " ");

        StepVerifier.create(adapter.selectEvidence("pregunta", List.of(EVIDENCE), "es"))
                .expectError(LlmUnavailableException.class)
                .verify();
    }
}
