package com.vidacruz.agent.infrastructure.adapter.in.rest;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.in.AskAssistantUseCase;
import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.SourceCitation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantControllerTest {

    private final AskAssistantUseCase askAssistant = mock(AskAssistantUseCase.class);
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(new AssistantController(askAssistant))
                .controllerAdvice(new ApiExceptionHandler())
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
                .jsonPath("$.mensaje").isEqualTo("Servicio de asistente no disponible temporalmente");
    }
}

