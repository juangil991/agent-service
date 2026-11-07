package com.vidacruz.agent.infrastructure.config;

import com.vidacruz.agent.infrastructure.adapter.in.rest.CorrelationIdWebFilter;
import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

@Configuration
class CorrelationLoggingConfiguration {

    @PostConstruct
    void registerCorrelationIdMdcAccessor() {
        String contextKey = CorrelationIdWebFilter.CONTEXT_KEY;
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                contextKey,
                () -> MDC.get(contextKey),
                value -> {
                    if (value == null) {
                        MDC.remove(contextKey);
                    } else {
                        MDC.put(contextKey, value);
                    }
                },
                () -> MDC.remove(contextKey));
    }
}
