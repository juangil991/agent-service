package com.vidacruz.agent.integration;

import com.vidacruz.agent.application.port.out.ProtocolRetrievalPort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.api-key=test-agent-breaker-key",
                "app.kafka.consumer.enabled=false",
                "app.assistant.ingestion.enabled=false",
                "app.assistant.llm.provider=openai",
                "app.assistant.llm.circuit-breaker.failure-window-size=2",
                "app.assistant.llm.circuit-breaker.failure-rate-threshold=100",
                "app.assistant.llm.circuit-breaker.open-state-wait-duration=300ms",
                "app.assistant.llm.circuit-breaker.permitted-calls-in-half-open-state=1",
                "spring.sql.init.mode=never"
        })
@AutoConfigureWebTestClient
class CircuitBreakerIntegrationTest {

    private static final AtomicInteger PROVIDER_REQUESTS = new AtomicInteger();
    private static final HttpServer PROVIDER = startProvider();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CircuitBreaker llmCircuitBreaker;

    @MockBean
    private ProtocolRetrievalPort retriever;

    @DynamicPropertySource
    static void configureProvider(DynamicPropertyRegistry registry) {
        registry.add(
                "app.assistant.llm.providers.openai.base-url",
                () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
        registry.add("app.assistant.llm.providers.openai.api-key", () -> "integration-test-key");
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
    }

    @BeforeEach
    void configureScenario() {
        PROVIDER_REQUESTS.set(0);
        llmCircuitBreaker.reset();
        webTestClient = webTestClient.mutate()
                .defaultHeader("X-API-Key", "test-agent-breaker-key")
                .build();
        when(retriever.retrieve(anyString(), anyInt()))
                .thenReturn(Mono.just(List.of(new ProtocolChunk(
                        "PROTO-TEST",
                        "Evaluación",
                        "La sepsis requiere antibióticos."))));
    }

    @Test
    void opensRejectsAndClosesAfterASuccessfulHalfOpenProbe() throws InterruptedException {
        query().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody().jsonPath("$.mensaje")
                .isEqualTo("Servicio de asistente no disponible temporalmente");
        query().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody().jsonPath("$.detalle").value(value -> assertThat((String) value)
                        .contains("proveedor de IA"));

        assertThat(llmCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(PROVIDER_REQUESTS).hasValue(2);

        query().expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(PROVIDER_REQUESTS).hasValue(2);

        Thread.sleep(400);
        query().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo("La sepsis requiere antibióticos.")
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-TEST");

        assertThat(PROVIDER_REQUESTS).hasValue(5);
        assertThat(llmCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private WebTestClient.ResponseSpec query() {
        return webTestClient.post()
                .uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"pregunta":"¿Cómo tratar la sepsis?"}
                        """)
                .exchange();
    }

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", CircuitBreakerIntegrationTest::respondToProviderCall);
            server.start();
            return server;
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static void respondToProviderCall(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int attempt = PROVIDER_REQUESTS.incrementAndGet();
        if (attempt <= 2) {
            writeResponse(exchange, 503,
                    "{\"error\":{\"message\":\"temporarily unavailable\",\"type\":\"server_error\"}}");
            return;
        }

        String content = request.contains("Your task is to evaluate if the response for the query")
                || request.contains("Evaluate whether or not the following claim is supported")
                ? "yes"
                : "La sepsis requiere antibióticos.";
        String response = """
                {
                  "id": "chatcmpl-integration",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "gpt-4o-mini",
                  "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": %s},
                    "finish_reason": "stop"
                  }]
                }
                """.formatted(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(content));
        writeResponse(exchange, 200, response);
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }
}
