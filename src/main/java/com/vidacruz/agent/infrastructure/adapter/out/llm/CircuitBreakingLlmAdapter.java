package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.vidacruz.agent.application.exception.LlmUnavailableException;
import com.vidacruz.agent.application.port.out.LlmPort;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Supplier;

public class CircuitBreakingLlmAdapter implements LlmPort {

    private final LlmPort delegate;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakingLlmAdapter(LlmPort delegate, CircuitBreaker circuitBreaker) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Mono<String> selectEvidence(String question, List<ProtocolChunk> evidence, String language) {
        return protect(() -> delegate.selectEvidence(question, evidence, language));
    }

    private <T> Mono<T> protect(Supplier<Mono<T>> operation) {
        return Mono.defer(operation)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(CallNotPermittedException.class, error -> new LlmUnavailableException(
                        "El circuito del proveedor de IA está abierto", error));
    }
}
