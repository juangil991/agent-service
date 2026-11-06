package com.vidacruz.agent.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.assistant.llm.provider=stub"})
@AutoConfigureWebTestClient
class AssistantFlowIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private WebTestClient webTestClient;

    @Test
    void respondsWithProtocolSourceForSepsisQuestion() {
        webTestClient.post()
                .uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "pregunta": "¿Cuándo se administra el primer antibiótico en sepsis?"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").value(value -> org.assertj.core.api.Assertions.assertThat((String) value)
                        .contains("PROTO-002"))
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-002");
    }

    @Test
    void returnsNoProtocolMessageWithoutHallucination() {
        webTestClient.post()
                .uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "pregunta": "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo("No existe un protocolo disponible para la pregunta.")
                .jsonPath("$.fuentes.length()").isEqualTo(0);
    }
}

