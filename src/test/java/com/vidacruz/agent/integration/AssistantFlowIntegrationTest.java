package com.vidacruz.agent.integration;

import com.vidacruz.agent.application.port.out.LlmPort;
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
                "app.security.api-key=test-agent-integration-key",
                "app.assistant.llm.provider=stub",
                "app.kafka.consumer.enabled=false",
                "spring.sql.init.mode=never"
        })
@AutoConfigureWebTestClient
class AssistantFlowIntegrationTest {

    private static final String PEDIATRIC_ORAL_QUESTION =
            "¿Cuál es la hidratación oral para deshidratación pediátrica leve o moderada?";
    private static final String PEDIATRIC_ORAL_QUOTE = "Solución oral 50 ml/kg en 4 horas.";
    private static final String PEDIATRIC_SEVERE_QUESTION =
            "¿Qué manejo indica PROTO-003 para deshidratación pediátrica severa?";
    private static final String PEDIATRIC_SEVERE_QUOTE = "Vía intravenosa, bolo de 20 ml/kg.";

    @org.springframework.beans.factory.annotation.Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ProtocolRetrievalPort retriever;

    @MockBean
    private LlmPort llm;

    @BeforeEach
    void configureRetriever() {
        webTestClient = webTestClient.mutate()
                .defaultHeader("X-API-Key", "test-agent-integration-key")
                .build();
        when(retriever.retrieve(anyString(), anyInt())).thenAnswer(invocation -> {
            String question = invocation.getArgument(0);
            List<ProtocolChunk> evidence;
            if (question.equals(PEDIATRIC_ORAL_QUESTION)) {
                evidence = List.of(new ProtocolChunk(
                        "chunk-proto-003-oral",
                        "PROTO-003",
                        "Deshidratación Pediátrica",
                        "Rehidratación",
                        "Leve o moderada sin vómito: " + PEDIATRIC_ORAL_QUOTE,
                        0.02));
            } else if (question.equals(PEDIATRIC_SEVERE_QUESTION)) {
                evidence = List.of(new ProtocolChunk(
                        "chunk-proto-003-severe",
                        "PROTO-003",
                        "Deshidratación Pediátrica",
                        "Rehidratación",
                        "Severa: " + PEDIATRIC_SEVERE_QUOTE,
                        0.02));
            } else if (question.contains("sepsis")) {
                evidence = List.of(new ProtocolChunk(
                            "chunk-sepsis",
                            "PROTO-002",
                            "Sepsis, Identificación Temprana (qSOFA)",
                            "Acciones iniciales",
                            "En sepsis, administrar antibiótico de amplio espectro en la primera hora.",
                            0.02));
            } else {
                evidence = List.of();
            }
            return Mono.just(evidence);
        });
        when(llm.selectEvidence(anyString(), org.mockito.ArgumentMatchers.anyList(), anyString()))
                .thenAnswer(invocation -> {
                    String question = invocation.getArgument(0);
                    if (question.equals(PEDIATRIC_ORAL_QUESTION)) {
                        return Mono.just("""
                                {"extractos":[{"chunkId":"chunk-proto-003-oral","texto":"Solución oral 50 ml/kg en 4 horas."}]}
                                """);
                    }
                    if (question.equals(PEDIATRIC_SEVERE_QUESTION)) {
                        return Mono.just("""
                                {"extractos":[{"chunkId":"chunk-proto-003-severe","texto":"Vía intravenosa, bolo de 20 ml/kg."}]}
                                """);
                    }
                    return Mono.just("""
                            {"extractos":[{"chunkId":"chunk-sepsis","texto":"En sepsis, administrar antibiótico de amplio espectro en la primera hora."}]}
                            """);
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
                .jsonPath("$.respuesta").isEqualTo(
                        "En sepsis, administrar antibiótico de amplio espectro en la primera hora.")
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-002");
    }

    @Test
    void respondsWithCitedOralRehydrationFromProto003() {
        webTestClient.post()
                .uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pregunta\":\"" + PEDIATRIC_ORAL_QUESTION + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo(PEDIATRIC_ORAL_QUOTE)
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-003")
                .jsonPath("$.fuentes[0].seccion").isEqualTo("Rehidratación");
    }

    @Test
    void respondsWithCitedIntravenousBolusFromProto003() {
        webTestClient.post()
                .uri("/asistente/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pregunta\":\"" + PEDIATRIC_SEVERE_QUESTION + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.respuesta").isEqualTo(PEDIATRIC_SEVERE_QUOTE)
                .jsonPath("$.fuentes[0].protocolo").isEqualTo("PROTO-003")
                .jsonPath("$.fuentes[0].seccion").isEqualTo("Rehidratación");
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

    @Test
    void returnsAbstentionWithoutSourcesWhenGeneratedTextIsNotInTheCorpus() {
        when(llm.selectEvidence(anyString(), org.mockito.ArgumentMatchers.anyList(), anyString()))
                .thenReturn(Mono.just("""
                        {"extractos":[{"chunkId":"chunk-sepsis","texto":"Administre corticoides para tratar sepsis."}]}
                        """));

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
                .jsonPath("$.respuesta").isEqualTo("No existe un protocolo disponible para la pregunta.")
                .jsonPath("$.fuentes.length()").isEqualTo(0);
    }
}

