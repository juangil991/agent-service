package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.application.port.out.ProtocolCorpusPort;
import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.service.ProtocolRetriever;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantQueryServiceTest {

    private final LlmPort llm = mock(LlmPort.class);
    private final ProtocolCorpusPort corpus = mock(ProtocolCorpusPort.class);
    private final AssistantQueryService service = new AssistantQueryService(
            llm, corpus, new ProtocolRetriever(), "es");

    @Test
    void noMatchReturnsAbstentionWithoutCallingLlm() {
        when(corpus.allChunks()).thenReturn(List.of(
                new ProtocolChunk("PROTO-001", "Medidas iniciales", "Reconfirmar la presión tras 5 minutos de reposo.")));

        StepVerifier.create(service.ask("Tratamiento de primera línea para diabetes tipo 2"))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).isEqualTo("No existe un protocolo disponible para la pregunta.");
                    assertThat(answer.fuentes()).isEmpty();
                })
                .verifyComplete();

        verifyNoInteractions(llm);
    }

    @Test
    void matchedEvidenceReturnsAnswerWithSources() {
        List<ProtocolChunk> evidence = List.of(
                new ProtocolChunk("PROTO-002", "Acciones iniciales", "En sepsis, administrar antibiótico de amplio espectro en la primera hora."));
        when(corpus.allChunks()).thenReturn(evidence);
        when(llm.generateAnswer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("es")))
                .thenReturn(Mono.just("Se recomienda antibiótico en la primera hora."));

        StepVerifier.create(service.ask("¿Cuándo se administra el primer antibiótico en sepsis?"))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).contains("primera hora");
                    assertThat(answer.fuentes()).hasSize(1);
                    assertThat(answer.fuentes().get(0).protocolo()).isEqualTo("PROTO-002");
                })
                .verifyComplete();
    }

    @Test
    void llmFailureBubblesAsUnavailable() {
        List<ProtocolChunk> evidence = List.of(
                new ProtocolChunk("PROTO-002", "Acciones iniciales", "En sepsis, administrar antibiótico de amplio espectro en la primera hora."));
        when(corpus.allChunks()).thenReturn(evidence);
        when(llm.generateAnswer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("es")))
                .thenReturn(Mono.error(new RuntimeException("upstream failed")));

        StepVerifier.create(service.ask("¿Cuándo se administra el primer antibiótico en sepsis?"))
                .expectError(LlmUnavailableException.class)
                .verify();
    }

    @Test
    void blankQuestionReturnsBadRequestError() {
        StepVerifier.create(service.ask("   "))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}

