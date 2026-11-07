package com.vidacruz.agent.integration;

import com.vidacruz.agent.application.port.out.ProtocolRetrievalPort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.assistant.llm.provider=stub",
                "app.kafka.consumer.enabled=false",
                "spring.sql.init.mode=never"
        })
@AutoConfigureWebTestClient
class AssistantFlowIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ProtocolRetrievalPort retriever;

    @BeforeEach
    void configureRetriever() {
        when(retriever.retrieve(anyString(), anyInt())).thenAnswer(invocation -> {
            String question = invocation.getArgument(0);
            List<ProtocolChunk> evidence = question.contains("sepsis")
                    ? List.of(new ProtocolChunk(
                            "PROTO-002",
                            "Acciones iniciales",
                            "En sepsis, administrar antibiótico de amplio espectro en la primera hora."))
                    : List.of();
            return Mono.just(evidence);
        });
    }

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

