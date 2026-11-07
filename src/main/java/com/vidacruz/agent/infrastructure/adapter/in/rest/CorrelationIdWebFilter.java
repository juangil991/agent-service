package com.vidacruz.agent.infrastructure.adapter.in.rest;

import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";
    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdWebFilter.class);
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = correlationId(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);

        ServerWebExchange correlatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER_NAME, correlationId)))
                .build();

        return chain.filter(correlatedExchange)
                .contextWrite(context -> context.put(CONTEXT_KEY, correlationId))
                .doFinally(signal -> logger.info(
                        "HTTP request completed",
                        StructuredArguments.keyValue("correlationId", correlationId),
                        StructuredArguments.keyValue("method", exchange.getRequest().getMethod().name()),
                        StructuredArguments.keyValue("path", exchange.getRequest().getURI().getPath()),
                        StructuredArguments.keyValue(
                                "status",
                                exchange.getResponse().getStatusCode() == null
                                        ? 0
                                        : exchange.getResponse().getStatusCode().value())));
    }

    private String correlationId(String suppliedId) {
        if (suppliedId != null && VALID_ID.matcher(suppliedId).matches()) {
            return suppliedId;
        }
        return UUID.randomUUID().toString();
    }
}
