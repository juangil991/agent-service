package com.vidacruz.agent.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.infrastructure.adapter.out.llm.StubLlmAdapter;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantConfigurationTest {

    private final AssistantConfiguration configuration = new AssistantConfiguration();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<ProtocolChunk> EVIDENCE = List.of(
            new ProtocolChunk("PROTO-001", "Sección", "Contenido"));

    @Test
    void returnsStubAdapterWhenStubProviderIsSelected() {
        LlmPort llm = configuration.llmPort(
                objectMapper,
                "stub",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "/v1/chat/completions",
                "gemini-2.0-flash",
                "",
                "https://api.groq.com/openai",
                "/v1/chat/completions",
                "llama-3.1-70b-versatile",
                "",
                "https://api.mistral.ai",
                "/v1/chat/completions",
                "mistral-large-latest",
                "",
                "https://api.openai.com",
                "/v1/chat/completions",
                "gpt-4o-mini",
                "");

        assertThat(llm).isInstanceOf(StubLlmAdapter.class);
    }

    @Test
    void unsupportedProviderReturnsUnavailableError() {
        LlmPort llm = configuration.llmPort(
                objectMapper,
                "anthropic",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "/v1/chat/completions",
                "gemini-2.0-flash",
                "google-key",
                "https://api.groq.com/openai",
                "/v1/chat/completions",
                "llama-3.1-70b-versatile",
                "groq-key",
                "https://api.mistral.ai",
                "/v1/chat/completions",
                "mistral-large-latest",
                "mistral-key",
                "https://api.openai.com",
                "/v1/chat/completions",
                "gpt-4o-mini",
                "openai-key");

        StepVerifier.create(llm.generateAnswer("pregunta", EVIDENCE, "es"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(LlmUnavailableException.class);
                    assertThat(error.getMessage()).contains("no soportado");
                })
                .verify();
    }

    @Test
    void selectedProviderWithoutApiKeyReturnsUnavailableError() {
        LlmPort llm = configuration.llmPort(
                objectMapper,
                "mistral",
                "https://generativelanguage.googleapis.com/v1beta/openai",
                "/v1/chat/completions",
                "gemini-2.0-flash",
                "google-key",
                "https://api.groq.com/openai",
                "/v1/chat/completions",
                "llama-3.1-70b-versatile",
                "groq-key",
                "https://api.mistral.ai",
                "/v1/chat/completions",
                "mistral-large-latest",
                "",
                "https://api.openai.com",
                "/v1/chat/completions",
                "gpt-4o-mini",
                "openai-key");

        StepVerifier.create(llm.generateAnswer("pregunta", EVIDENCE, "es"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(LlmUnavailableException.class);
                    assertThat(error.getMessage()).contains("Mistral");
                })
                .verify();
    }

    @Test
    void upstreamServiceUnavailableIsReturnedAfterOneAttempt() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = """
                    {"error":{"message":"temporarily unavailable","type":"server_error"}}
                    """.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, body.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(body);
            }
        });
        server.start();

        try {
            LlmPort llm = configuration.llmPort(
                    objectMapper,
                    "openai",
                    "https://generativelanguage.googleapis.com/v1beta/openai",
                    "/v1/chat/completions",
                    "gemini-2.0-flash",
                    "google-key",
                    "https://api.groq.com/openai",
                    "/v1/chat/completions",
                    "llama-3.1-70b-versatile",
                    "groq-key",
                    "https://api.mistral.ai",
                    "/v1/chat/completions",
                    "mistral-large-latest",
                    "mistral-key",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "/v1/chat/completions",
                    "gpt-4o-mini",
                    "test-key");

            StepVerifier.create(llm.generateAnswer("pregunta", EVIDENCE, "es"))
                    .expectError(LlmUnavailableException.class)
                    .verify(java.time.Duration.ofSeconds(5));

            assertThat(requests).hasValue(1);
        } finally {
            server.stop(0);
        }
    }
}
