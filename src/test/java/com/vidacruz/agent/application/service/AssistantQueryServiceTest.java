package com.vidacruz.agent.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.application.port.out.ProtocolRetrievalPort;
import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.model.SourceCitation;
import com.vidacruz.agent.domain.service.ProtocolEvidenceRelevance;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantQueryServiceTest {

    private static final String SEPSIS_QUESTION = "¿Cuándo se administra el primer antibiótico en sepsis?";
    private static final String SEPSIS_QUOTE = "Iniciar antibiótico de amplio espectro dentro de la primera hora.";
    private static final ProtocolChunk SEPSIS_CHUNK = chunk(
            "chunk-sepsis",
            "PROTO-002",
            "Sepsis, Identificación Temprana (qSOFA)",
            "Acciones Iniciales",
            SEPSIS_QUOTE);
    private static final String CRISIS_QUESTION =
            "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?";
    private static final String CRISIS_QUOTE = "Reconfirmar la presión arterial tras 5 minutos de reposo.";
    private static final ProtocolChunk CRISIS_CHUNK = chunk(
            "chunk-crisis",
            "PROTO-001",
            "Crisis Hipertensiva, Manejo Inicial",
            "Medidas Iniciales",
            CRISIS_QUOTE);
    private static final String PEDIATRIC_ORAL_QUESTION =
            "¿Cuál es la hidratación oral para deshidratación pediátrica leve o moderada?";
    private static final String PEDIATRIC_ORAL_QUOTE = "Solución oral 50 ml/kg en 4 horas.";
    private static final ProtocolChunk PEDIATRIC_ORAL_CHUNK = chunk(
            "chunk-proto-003-oral",
            "PROTO-003",
            "Deshidratación Pediátrica",
            "Rehidratación",
            "Leve o moderada sin vómito: " + PEDIATRIC_ORAL_QUOTE);
    private static final String PEDIATRIC_SEVERE_QUESTION =
            "¿Qué manejo indica PROTO-003 para deshidratación pediátrica severa?";
    private static final String PEDIATRIC_SEVERE_QUOTE = "Vía intravenosa, bolo de 20 ml/kg.";
    private static final ProtocolChunk PEDIATRIC_SEVERE_CHUNK = chunk(
            "chunk-proto-003-severe",
            "PROTO-003",
            "Deshidratación Pediátrica",
            "Rehidratación",
            "Severa: " + PEDIATRIC_SEVERE_QUOTE);

    private final LlmPort llm = mock(LlmPort.class);
    private final ProtocolRetrievalPort retriever = mock(ProtocolRetrievalPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AssistantQueryService service = new AssistantQueryService(
            llm,
            retriever,
            "es",
            3,
            meterRegistry,
            objectMapper,
            new ProtocolEvidenceRelevance());

    @Test
    void noEvidenceReturnsAbstentionWithoutCallingLlm() {
        when(retriever.retrieve("Tratamiento de primera línea para diabetes tipo 2", 3))
                .thenReturn(Mono.just(List.of()));

        assertAbstains("Tratamiento de primera línea para diabetes tipo 2");
        verifyNoInteractions(llm);
    }

    @Test
    void rejectsRetrievedEvidenceBelowTheDeterministicRelevanceGate() {
        when(retriever.retrieve("¿Cuál es el tratamiento para diabetes tipo 2?", 3))
                .thenReturn(Mono.just(List.of(SEPSIS_CHUNK)));

        assertAbstains("¿Cuál es el tratamiento para diabetes tipo 2?");
        verifyNoInteractions(llm);
    }

    @Test
    void returnsOnlyAnExactSelectedExcerptAndDerivesItsCitationFromTheCorpusChunk() throws Exception {
        when(retriever.retrieve(SEPSIS_QUESTION, 3)).thenReturn(Mono.just(List.of(SEPSIS_CHUNK)));
        when(llm.selectEvidence(SEPSIS_QUESTION, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.just(selection(SEPSIS_CHUNK, SEPSIS_QUOTE)));

        StepVerifier.create(service.ask(SEPSIS_QUESTION))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).isEqualTo(SEPSIS_QUOTE);
                    assertThat(answer.fuentes()).containsExactly(
                            new SourceCitation("PROTO-002", "Acciones Iniciales"));
                })
                .verifyComplete();

        assertThat(meterRegistry.get("vidacruz.llm.latency")
                .tag("operation", "select_evidence")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void returnsTheVerifiedOralRehydrationExcerptAndProto003Citation() throws Exception {
        when(retriever.retrieve(PEDIATRIC_ORAL_QUESTION, 3))
                .thenReturn(Mono.just(List.of(PEDIATRIC_ORAL_CHUNK)));
        when(llm.selectEvidence(PEDIATRIC_ORAL_QUESTION, List.of(PEDIATRIC_ORAL_CHUNK), "es"))
                .thenReturn(Mono.just(selection(PEDIATRIC_ORAL_CHUNK, PEDIATRIC_ORAL_QUOTE)));

        StepVerifier.create(service.ask(PEDIATRIC_ORAL_QUESTION))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).isEqualTo(PEDIATRIC_ORAL_QUOTE);
                    assertThat(answer.fuentes()).containsExactly(
                            new SourceCitation("PROTO-003", "Rehidratación"));
                })
                .verifyComplete();
    }

    @Test
    void returnsTheVerifiedSevereRehydrationExcerptAndProto003Citation() throws Exception {
        when(retriever.retrieve(PEDIATRIC_SEVERE_QUESTION, 3))
                .thenReturn(Mono.just(List.of(PEDIATRIC_SEVERE_CHUNK)));
        when(llm.selectEvidence(PEDIATRIC_SEVERE_QUESTION, List.of(PEDIATRIC_SEVERE_CHUNK), "es"))
                .thenReturn(Mono.just(selection(PEDIATRIC_SEVERE_CHUNK, PEDIATRIC_SEVERE_QUOTE)));

        StepVerifier.create(service.ask(PEDIATRIC_SEVERE_QUESTION))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).isEqualTo(PEDIATRIC_SEVERE_QUOTE);
                    assertThat(answer.fuentes()).containsExactly(
                            new SourceCitation("PROTO-003", "Rehidratación"));
                })
                .verifyComplete();
    }

    @Test
    void abstainsWhenProto003EvidenceDoesNotAnswerTheQuestion() {
        String question = "¿Cuál es el tratamiento de primera línea para diabetes tipo 2?";
        when(retriever.retrieve(question, 3)).thenReturn(Mono.just(List.of(PEDIATRIC_ORAL_CHUNK)));

        assertAbstains(question);

        verifyNoInteractions(llm);
    }

    @Test
    void rejectsUnknownChunkIdsEvenWhenTheClaimLooksSupported() throws Exception {
        when(retriever.retrieve(SEPSIS_QUESTION, 3)).thenReturn(Mono.just(List.of(SEPSIS_CHUNK)));
        when(llm.selectEvidence(SEPSIS_QUESTION, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.just(selection("PROTO-002", SEPSIS_QUOTE)));

        assertAbstains(SEPSIS_QUESTION);
    }

    @Test
    void rejectsAValidChunkWithAQuoteFromAnotherChunk() throws Exception {
        String question = CRISIS_QUESTION + " y antibiótico en sepsis";
        List<ProtocolChunk> evidence = List.of(CRISIS_CHUNK, SEPSIS_CHUNK);
        when(retriever.retrieve(question, 3)).thenReturn(Mono.just(evidence));
        when(llm.selectEvidence(question, evidence, "es"))
                .thenReturn(Mono.just(selection(CRISIS_CHUNK, SEPSIS_QUOTE)));

        assertAbstains(question);
    }

    @Test
    void rejectsAddedClinicalTextAndParaphrases() throws Exception {
        String question = "¿Cuándo se administra el primer antibiótico en sepsis?";
        when(retriever.retrieve(question, 3)).thenReturn(Mono.just(List.of(SEPSIS_CHUNK)));
        when(llm.selectEvidence(question, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.just(selection(
                        SEPSIS_CHUNK,
                        SEPSIS_QUOTE + " Además, administrar corticoides.")));

        assertAbstains(question);
    }

    @Test
    void rejectsMalformedOrEmptySelections() throws Exception {
        when(retriever.retrieve(SEPSIS_QUESTION, 3)).thenReturn(Mono.just(List.of(SEPSIS_CHUNK)));
        when(llm.selectEvidence(SEPSIS_QUESTION, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.just("{not-json"));
        assertAbstains(SEPSIS_QUESTION);

        when(llm.selectEvidence(SEPSIS_QUESTION, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.just(selection(SEPSIS_CHUNK, SEPSIS_QUOTE) + " texto clínico añadido"));
        assertAbstains(SEPSIS_QUESTION);

        when(llm.selectEvidence(SEPSIS_QUESTION, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.just("{\"extractos\":[]}"));
        assertAbstains(SEPSIS_QUESTION);
    }

    @Test
    void rejectsSelectionsThatAddUntrustedCitationFields() throws Exception {
        when(retriever.retrieve(SEPSIS_QUESTION, 3)).thenReturn(Mono.just(List.of(SEPSIS_CHUNK)));
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode excerpts = response.putArray("extractos");
        ObjectNode excerpt = excerpts.addObject();
        excerpt.put("chunkId", SEPSIS_CHUNK.chunkId());
        excerpt.put("texto", SEPSIS_QUOTE);
        excerpt.put("protocolo", "PROTO-FAKE");
        when(llm.selectEvidence(SEPSIS_QUESTION, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.just(objectMapper.writeValueAsString(response)));

        assertAbstains(SEPSIS_QUESTION);
    }

    @Test
    void combinesMultipleVerifiedExcerptsAndCitesBothSources() throws Exception {
        String question = "¿Qué medidas iniciales hay ante una crisis hipertensiva y sepsis con antibiótico?";
        List<ProtocolChunk> evidence = List.of(CRISIS_CHUNK, SEPSIS_CHUNK);
        when(retriever.retrieve(question, 3)).thenReturn(Mono.just(evidence));
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode excerpts = response.putArray("extractos");
        addExcerpt(excerpts, CRISIS_CHUNK, CRISIS_QUOTE);
        addExcerpt(excerpts, SEPSIS_CHUNK, SEPSIS_QUOTE);
        when(llm.selectEvidence(question, evidence, "es"))
                .thenReturn(Mono.just(objectMapper.writeValueAsString(response)));

        StepVerifier.create(service.ask(question))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).isEqualTo(CRISIS_QUOTE + "\n" + SEPSIS_QUOTE);
                    assertThat(answer.fuentes()).containsExactly(
                            new SourceCitation("PROTO-001", "Medidas Iniciales"),
                            new SourceCitation("PROTO-002", "Acciones Iniciales"));
                })
                .verifyComplete();
    }

    @Test
    void llmFailureBubblesAsUnavailable() {
        when(retriever.retrieve(SEPSIS_QUESTION, 3)).thenReturn(Mono.just(List.of(SEPSIS_CHUNK)));
        when(llm.selectEvidence(SEPSIS_QUESTION, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.error(new RuntimeException("upstream failed")));

        StepVerifier.create(service.ask(SEPSIS_QUESTION))
                .expectError(LlmUnavailableException.class)
                .verify();
    }

    @Test
    void emptyLlmResponseBubblesAsUnavailable() {
        when(retriever.retrieve(SEPSIS_QUESTION, 3)).thenReturn(Mono.just(List.of(SEPSIS_CHUNK)));
        when(llm.selectEvidence(SEPSIS_QUESTION, List.of(SEPSIS_CHUNK), "es"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.ask(SEPSIS_QUESTION))
                .expectError(LlmUnavailableException.class)
                .verify();
    }

    @Test
    void blankQuestionReturnsBadRequestError() {
        StepVerifier.create(service.ask("   "))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(retriever, never()).retrieve(anyString(), eq(3));
    }

    private void assertAbstains(String question) {
        StepVerifier.create(service.ask(question))
                .assertNext(answer -> {
                    assertThat(answer.respuesta())
                            .isEqualTo("No existe un protocolo disponible para la pregunta.");
                    assertThat(answer.fuentes()).isEmpty();
                })
                .verifyComplete();
    }

    private String selection(ProtocolChunk chunk, String quote) throws Exception {
        ObjectNode response = objectMapper.createObjectNode();
        addExcerpt(response.putArray("extractos"), chunk, quote);
        return objectMapper.writeValueAsString(response);
    }

    private String selection(String chunkId, String quote) throws Exception {
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode excerpts = response.putArray("extractos");
        ObjectNode excerpt = excerpts.addObject();
        excerpt.put("chunkId", chunkId);
        excerpt.put("texto", quote);
        return objectMapper.writeValueAsString(response);
    }

    private void addExcerpt(ArrayNode excerpts, ProtocolChunk chunk, String quote) {
        ObjectNode excerpt = excerpts.addObject();
        excerpt.put("chunkId", chunk.chunkId());
        excerpt.put("texto", quote);
    }

    private static ProtocolChunk chunk(
            String chunkId,
            String protocol,
            String title,
            String section,
            String content) {
        return new ProtocolChunk(chunkId, protocol, title, section, content, 0.02);
    }
}
