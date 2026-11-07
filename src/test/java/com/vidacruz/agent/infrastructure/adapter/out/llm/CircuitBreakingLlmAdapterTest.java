package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CircuitBreakingLlmAdapterTest {

    private static final List<ProtocolChunk> EVIDENCE = List.of(
            new ProtocolChunk("PROTO-001", "Sección", "Contenido"));

    @Test
    void opensAfterFiveFailuresAndRejectsFurtherLlmCallsAsUnavailable() {
        LlmPort provider = mock(LlmPort.class);
        when(provider.generateAnswer("pregunta", EVIDENCE, "es"))
                .thenReturn(Mono.error(new RuntimeException("upstream failed")));
        CircuitBreaker circuitBreaker = circuitBreaker(Duration.ofSeconds(30));
        CircuitBreakingLlmAdapter adapter = new CircuitBreakingLlmAdapter(provider, circuitBreaker);

        for (int attempt = 0; attempt < 5; attempt++) {
            StepVerifier.create(adapter.generateAnswer("pregunta", EVIDENCE, "es"))
                    .expectError(RuntimeException.class)
                    .verify();
        }

        StepVerifier.create(adapter.generateAnswer("pregunta", EVIDENCE, "es"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(LlmUnavailableException.class);
                    assertThat(error.getMessage()).contains("circuito");
                })
                .verify();

        verify(provider, times(5)).generateAnswer("pregunta", EVIDENCE, "es");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void successfulHalfOpenProbeClosesCircuitAndAllowsVerification() {
        LlmPort provider = mock(LlmPort.class);
        when(provider.generateAnswer("pregunta", EVIDENCE, "es")).thenReturn(Mono.just("respuesta"));
        when(provider.evaluateAnswer("pregunta", "respuesta", EVIDENCE))
                .thenReturn(Mono.just(true));
        CircuitBreaker circuitBreaker = circuitBreaker(Duration.ofSeconds(30));
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        CircuitBreakingLlmAdapter adapter = new CircuitBreakingLlmAdapter(provider, circuitBreaker);

        StepVerifier.create(adapter.generateAnswer("pregunta", EVIDENCE, "es"))
                .expectNext("respuesta")
                .verifyComplete();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        StepVerifier.create(adapter.evaluateAnswer("pregunta", "respuesta", EVIDENCE))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void verificationFailuresAlsoOpenCircuitForGenerationCalls() {
        LlmPort provider = mock(LlmPort.class);
        when(provider.evaluateAnswer("pregunta", "respuesta", EVIDENCE))
                .thenReturn(Mono.error(new RuntimeException("upstream failed")));
        CircuitBreaker circuitBreaker = circuitBreaker(Duration.ofSeconds(30));
        CircuitBreakingLlmAdapter adapter = new CircuitBreakingLlmAdapter(provider, circuitBreaker);

        for (int attempt = 0; attempt < 5; attempt++) {
            StepVerifier.create(adapter.evaluateAnswer("pregunta", "respuesta", EVIDENCE))
                    .expectError(RuntimeException.class)
                    .verify();
        }

        StepVerifier.create(adapter.generateAnswer("pregunta", EVIDENCE, "es"))
                .expectError(LlmUnavailableException.class)
                .verify();

        verify(provider, times(5)).evaluateAnswer("pregunta", "respuesta", EVIDENCE);
    }

    private CircuitBreaker circuitBreaker(Duration openWaitDuration) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindow(5, 5, SlidingWindowType.COUNT_BASED)
                .failureRateThreshold(100)
                .waitDurationInOpenState(openWaitDuration)
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        return CircuitBreaker.of("test-llm", config);
    }
}
