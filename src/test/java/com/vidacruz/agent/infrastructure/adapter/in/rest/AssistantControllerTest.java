package com.vidacruz.agent.infrastructure.adapter.in.rest;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.in.AskAssistantUseCase;
import com.vidacruz.agent.application.port.in.GetUrgencyStatusUseCase;
import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.SourceCitation;
import com.vidacruz.agent.domain.model.TriageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantControllerTest {

    private final AskAssistantUseCase askAssistant = mock(AskAssistantUseCase.class);
    private final GetUrgencyStatusUseCase getUrgencyStatus = mock(GetUrgencyStatusUseCase.class);
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new AssistantController(askAssistant, getUrgencyStatus))
                .controllerAdvice(new ApiExceptionHandler())
                .webFilter(new CorrelationIdWebFilter())
                .build();
    }

    @Test
    void validRequestReturnsSources() {
        when(askAssistant.ask(anyString())).thenReturn(Mono.just(new AssistantAnswer(
                "Respuesta clínica controlada",
                List.of(new SourceCitation("PROTO-001", "Medidas iniciales")))));

        client.post()
                .uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pregunta\":\"¿Crisis hipertensiva?\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo("Respuesta clínica controlada")
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-001");
    }

    @Test
    void blankQuestionReturnsBadRequest() {
        client.post()
                .uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pregunta\":\"   \"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.mensaje").isEqualTo("pregunta es obligatoria");
    }

    @Test
    void llmUnavailableReturns503() {
        when(askAssistant.ask(anyString()))
                .thenReturn(Mono.error(new LlmUnavailableException("downstream")));

        client.post()
                .uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pregunta\":\"¿Qué hacer ante sepsis?\"}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.mensaje").isEqualTo("Servicio de asistente no disponible temporalmente")
                .jsonPath("$.detalle").isEqualTo("downstream");
    }

    @Test
    void urgencyStatusReturnsActiveCountsByTriageLevel() {
        when(getUrgencyStatus.getActiveCounts()).thenReturn(Mono.just(Map.of(
                TriageLevel.CRITICO, 2L,
                TriageLevel.URGENTE, 1L,
                TriageLevel.URGENTE_2, 0L,
                TriageLevel.NO_URGENTE, 3L,
                TriageLevel.NO_URGENTE_2, 0L)));

        client.get()
                .uri("/asistente/estado-urgencias")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pacientesActivosPorNivel.CRITICO").isEqualTo(2)
                .jsonPath("$.pacientesActivosPorNivel.URGENTE").isEqualTo(1)
                .jsonPath("$.pacientesActivosPorNivel.NO_URGENTE").isEqualTo(3);
    }

    @Test
    void suppliedCorrelationIdIsReturnedInResponse() {
        when(getUrgencyStatus.getActiveCounts()).thenReturn(Mono.just(Map.of()));

        client.get()
                .uri("/asistente/estado-urgencias")
                .header(CorrelationIdWebFilter.HEADER_NAME, "trace-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationIdWebFilter.HEADER_NAME, "trace-123");
    }

    @Test
    void missingCorrelationIdIsGeneratedAndReturned() {
        when(getUrgencyStatus.getActiveCounts()).thenReturn(Mono.just(Map.of()));

        client.get()
                .uri("/asistente/estado-urgencias")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(CorrelationIdWebFilter.HEADER_NAME,
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}

