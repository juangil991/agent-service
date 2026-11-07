package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.application.port.out.ProtocolRetrievalPort;
import com.vidacruz.agent.domain.model.AnswerVerification;
import com.vidacruz.agent.domain.model.AssistantAnswer;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantQueryServiceTest {

    private final LlmPort llm = mock(LlmPort.class);
    private final ProtocolRetrievalPort retriever = mock(ProtocolRetrievalPort.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AssistantQueryService service = new AssistantQueryService(
            llm, retriever, "es", 3, meterRegistry);

    @Test
    void noMatchReturnsAbstentionWithoutCallingLlm() {
        when(retriever.retrieve("Tratamiento de primera línea para diabetes tipo 2", 3))
                .thenReturn(Mono.just(List.of()));

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
        when(retriever.retrieve("¿Cuándo se administra el primer antibiótico en sepsis?", 3))
                .thenReturn(Mono.just(evidence));
        when(llm.generateAnswer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("es")))
                .thenReturn(Mono.just("Se recomienda antibiótico en la primera hora."));
        when(llm.verifyAnswer(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Mono.just(verification(true)));

        StepVerifier.create(service.ask("¿Cuándo se administra el primer antibiótico en sepsis?"))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).contains("primera hora");
                    assertThat(answer.fuentes()).hasSize(1);
                    assertThat(answer.fuentes().get(0).protocolo()).isEqualTo("PROTO-002");
                })
                .verifyComplete();

        assertThat(meterRegistry.get("vidacruz.llm.latency")
                .tag("operation", "generate_answer")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("vidacruz.llm.latency")
                .tag("operation", "verify_answer")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void generatedAbstentionIsReturnedWithoutVerificationOrSources() {
        List<ProtocolChunk> evidence = List.of(
                new ProtocolChunk("PROTO-002", "Acciones iniciales", "Contenido relacionado."));
        when(retriever.retrieve("Pregunta sin respuesta en el contexto", 3))
                .thenReturn(Mono.just(evidence));
        when(llm.generateAnswer("Pregunta sin respuesta en el contexto", evidence, "es"))
                .thenReturn(Mono.just(AssistantQueryService.NO_PROTOCOL_MESSAGE));

        StepVerifier.create(service.ask("Pregunta sin respuesta en el contexto"))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).isEqualTo(AssistantQueryService.NO_PROTOCOL_MESSAGE);
                    assertThat(answer.fuentes()).isEmpty();
                })
                .verifyComplete();

        verify(llm, never()).verifyAnswer(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void unsupportedClaimReturnsAbstentionWithoutSources() {
        List<ProtocolChunk> evidence = List.of(
                new ProtocolChunk("PROTO-002", "Acciones iniciales", "En sepsis, administrar antibiótico de amplio espectro en la primera hora."));
        when(retriever.retrieve("¿Cuándo se administra el primer antibiótico en sepsis?", 3))
                .thenReturn(Mono.just(evidence));
        when(llm.generateAnswer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("es")))
                .thenReturn(Mono.just("Administre antibiótico en la primera hora y agregue corticoides."));
        when(llm.verifyAnswer(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Mono.just(new AnswerVerification(List.of(
                        new AnswerVerification.ClaimAssessment("antibiótico en la primera hora", true),
                        new AnswerVerification.ClaimAssessment("agregar corticoides", false)))));

        StepVerifier.create(service.ask("¿Cuándo se administra el primer antibiótico en sepsis?"))
                .assertNext(answer -> {
                    assertThat(answer.respuesta()).isEqualTo(AssistantQueryService.NO_PROTOCOL_MESSAGE);
                    assertThat(answer.fuentes()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void llmFailureBubblesAsUnavailable() {
        List<ProtocolChunk> evidence = List.of(
                new ProtocolChunk("PROTO-002", "Acciones iniciales", "En sepsis, administrar antibiótico de amplio espectro en la primera hora."));
        when(retriever.retrieve("¿Cuándo se administra el primer antibiótico en sepsis?", 3))
                .thenReturn(Mono.just(evidence));
        when(llm.generateAnswer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("es")))
                .thenReturn(Mono.error(new RuntimeException("upstream failed")));

        StepVerifier.create(service.ask("¿Cuándo se administra el primer antibiótico en sepsis?"))
                .expectError(LlmUnavailableException.class)
                .verify();
    }

    @Test
    void emptyGeneratedAnswerBubblesAsUnavailable() {
        when(retriever.retrieve("¿Cuándo se administra el primer antibiótico en sepsis?", 3))
                .thenReturn(Mono.just(List.of(
                        new ProtocolChunk("PROTO-002", "Acciones iniciales", "Contenido."))));
        when(llm.generateAnswer(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq("es")))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.ask("¿Cuándo se administra el primer antibiótico en sepsis?"))
                .expectError(LlmUnavailableException.class)
                .verify();
    }

    @Test
    void verificationFailureBubblesAsUnavailable() {
        List<ProtocolChunk> evidence = List.of(
                new ProtocolChunk("PROTO-002", "Acciones iniciales", "En sepsis, administrar antibiótico de amplio espectro en la primera hora."));
        when(retriever.retrieve("¿Cuándo se administra el primer antibiótico en sepsis?", 3))
                .thenReturn(Mono.just(evidence));
        when(llm.generateAnswer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq("es")))
                .thenReturn(Mono.just("Se recomienda antibiótico en la primera hora."));
        when(llm.verifyAnswer(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()))
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

    private AnswerVerification verification(boolean supported) {
        return new AnswerVerification(List.of(
                new AnswerVerification.ClaimAssessment("antibiótico en la primera hora", supported)));
    }
}

