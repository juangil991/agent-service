package com.vidacruz.agent.infrastructure.adapter.out.embedding;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class VoyageAiEmbeddingAdapterTest {

    @Test
    void sendsQueryAndDocumentInputTypesToVoyage() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String vector = IntStream.range(0, 1024)
                .mapToObj(index -> index == 0 ? "0.125" : "0.0")
                .collect(Collectors.joining(","));
        byte[] response = ("{\"data\":[{\"embedding\":[" + vector + "],\"index\":0}]}")
                .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var responseBody = exchange.getResponseBody()) {
                responseBody.write(response);
            }
        });
        server.start();

        try {
            VoyageAiEmbeddingAdapter adapter = new VoyageAiEmbeddingAdapter(
                    WebClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-key",
                    "voyage-4",
                    Duration.ofMillis(1));

            var queryEmbedding = adapter.embedQuery("¿Qué hacer ante sepsis?")
                    .block(Duration.ofSeconds(5));
            assertThat(queryEmbedding).hasSize(1024);
            assertThat(queryEmbedding.get(0)).isEqualTo(0.125);
            assertThat(requestBody.get()).contains("\"input_type\":\"query\"");
            assertThat(requestBody.get()).contains("\"model\":\"voyage-4\"");

            adapter.embedDocuments(java.util.List.of("Administrar antibiótico."))
                    .block(Duration.ofSeconds(5));
            assertThat(requestBody.get()).contains("\"input_type\":\"document\"");
        } finally {
            server.stop(0);
        }
    }
}
