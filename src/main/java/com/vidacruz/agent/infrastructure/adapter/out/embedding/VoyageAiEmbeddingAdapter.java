package com.vidacruz.agent.infrastructure.adapter.out.embedding;

import com.vidacruz.agent.application.exception.RagUnavailableException;
import com.vidacruz.agent.application.port.out.ProtocolEmbeddingPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Component
public class VoyageAiEmbeddingAdapter implements ProtocolEmbeddingPort {

    private static final int EMBEDDING_DIMENSIONS = 1024;
    // The extra second keeps three starts outside any rolling 60-second boundary.
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofSeconds(21);

    private final WebClient client;
    private final String apiKey;
    private final String model;
    private final EmbeddingRequestRateLimiter requestRateLimiter;

    @Autowired
    public VoyageAiEmbeddingAdapter(
            WebClient.Builder webClientBuilder,
            @Value("${app.assistant.embeddings.base-url:https://api.voyageai.com/v1}") String baseUrl,
            @Value("${app.assistant.embeddings.api-key:}") String apiKey,
            @Value("${app.assistant.embeddings.model:voyage-4}") String model) {
        this(webClientBuilder, baseUrl, apiKey, model, MIN_REQUEST_INTERVAL);
    }

    VoyageAiEmbeddingAdapter(
            WebClient.Builder webClientBuilder,
            String baseUrl,
            String apiKey,
            String model,
            Duration requestInterval) {
        this.client = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.requestRateLimiter = new EmbeddingRequestRateLimiter(requestInterval);
    }

    @Override
    public Mono<List<List<Double>>> embedDocuments(List<String> texts) {
        return embed(texts, "document");
    }

    @Override
    public Mono<List<Double>> embedQuery(String question) {
        if (question == null || question.isBlank()) {
            return Mono.error(new IllegalArgumentException("La pregunta para generar embedding es obligatoria"));
        }
        return embed(List.of(question.trim()), "query")
                .map(embeddings -> embeddings.get(0));
    }

    private Mono<List<List<Double>>> embed(List<String> texts, String inputType) {
        if (texts.isEmpty()) {
            return Mono.just(List.of());
        }
        if (apiKey.isBlank()) {
            return Mono.error(new RagUnavailableException("Voyage AI no está configurado: falta VOYAGE_API_KEY"));
        }

        EmbeddingRequest request = new EmbeddingRequest(model, texts, inputType, EMBEDDING_DIMENSIONS, "float");
        return requestRateLimiter.acquire()
                .then(Mono.defer(() -> client.post()
                        .uri("/embeddings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(EmbeddingResponse.class)))
                .switchIfEmpty(Mono.error(new RagUnavailableException("Voyage AI devolvió una respuesta vacía")))
                .map(response -> validateResponse(response, texts.size()))
                .onErrorMap(error -> error instanceof RagUnavailableException
                        ? error
                        : new RagUnavailableException("No se pudieron generar embeddings con Voyage AI", error));
    }

    private List<List<Double>> validateResponse(EmbeddingResponse response, int expectedCount) {
        if (response.data() == null || response.data().size() != expectedCount) {
            throw new RagUnavailableException("Voyage AI devolvió una cantidad de embeddings inesperada");
        }

        List<EmbeddingData> ordered = response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingData::index))
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            List<Double> vector = ordered.get(i).embedding();
            if (ordered.get(i).index() != i || vector == null || vector.size() != EMBEDDING_DIMENSIONS
                    || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new RagUnavailableException("Voyage AI devolvió un embedding inválido");
            }
        }
        return ordered.stream().map(EmbeddingData::embedding).toList();
    }

    private record EmbeddingRequest(
            String model,
            List<String> input,
            String input_type,
            int output_dimension,
            String output_dtype) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Double> embedding, int index) {
    }
}
