package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.vidacruz.agent.domain.model.ProtocolChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SpringAiOpenAiCompatibleLlmAdapterTest {

    private static final List<ProtocolChunk> EVIDENCE = List.of(
            new ProtocolChunk("PROTO-001", "Sección de evaluación", "Contenido de respaldo"));

    @Test
    void evaluatesQuestionAnswerAndRetrievedContextWithBothEvaluators() {
        AtomicReference<EvaluationRequest> relevancyRequest = new AtomicReference<>();
        AtomicReference<EvaluationRequest> factCheckingRequest = new AtomicReference<>();
        Evaluator relevancy = request -> {
            relevancyRequest.set(request);
            return response(true);
        };
        Evaluator factChecking = request -> {
            factCheckingRequest.set(request);
            return response(true);
        };
        SpringAiOpenAiCompatibleLlmAdapter adapter = adapter(relevancy, factChecking);

        StepVerifier.create(adapter.evaluateAnswer("pregunta clínica", "respuesta clínica", EVIDENCE))
                .expectNext(true)
                .verifyComplete();

        assertRequest(relevancyRequest.get());
        assertRequest(factCheckingRequest.get());
    }

    @Test
    void rejectsAnswerWhenEitherEvaluatorFails() {
        SpringAiOpenAiCompatibleLlmAdapter relevanceFailure = adapter(
                request -> response(false),
                request -> response(true));
        SpringAiOpenAiCompatibleLlmAdapter factCheckFailure = adapter(
                request -> response(true),
                request -> response(false));

        StepVerifier.create(relevanceFailure.evaluateAnswer("pregunta", "respuesta", EVIDENCE))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(factCheckFailure.evaluateAnswer("pregunta", "respuesta", EVIDENCE))
                .expectNext(false)
                .verifyComplete();
    }

    private SpringAiOpenAiCompatibleLlmAdapter adapter(Evaluator relevancy, Evaluator factChecking) {
        return new SpringAiOpenAiCompatibleLlmAdapter(
                mock(ChatClient.class),
                "proveedor de prueba",
                "test-key",
                relevancy,
                factChecking);
    }

    private EvaluationResponse response(boolean pass) {
        return new EvaluationResponse(pass, 1, "", Map.of());
    }

    private void assertRequest(EvaluationRequest request) {
        assertThat(request.getUserText()).isEqualTo("pregunta clínica");
        assertThat(request.getResponseContent()).isEqualTo("respuesta clínica");
        assertThat(request.getDataList())
                .extracting(document -> document.getText())
                .containsExactly("[PROTO-001 | Sección de evaluación] Contenido de respaldo");
    }
}
