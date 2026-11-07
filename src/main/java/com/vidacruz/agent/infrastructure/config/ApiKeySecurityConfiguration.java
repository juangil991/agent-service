package com.vidacruz.agent.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Configuration
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeySecurityConfiguration {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    ReactiveAuthenticationManager apiKeyAuthenticationManager(ApiKeyProperties properties) {
        byte[] expectedApiKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
        return authentication -> {
            Object credentials = authentication.getCredentials();
            if (credentials instanceof String apiKey
                    && MessageDigest.isEqual(expectedApiKey, apiKey.getBytes(StandardCharsets.UTF_8))) {
                Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                        "api-key-client", null, List.of());
                return Mono.just(authenticated);
            }
            return Mono.error(new BadCredentialsException("Invalid API key"));
        };
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveAuthenticationManager apiKeyAuthenticationManager) {
        AuthenticationWebFilter apiKeyFilter = new AuthenticationWebFilter(apiKeyAuthenticationManager);
        apiKeyFilter.setServerAuthenticationConverter(exchange -> {
            String path = exchange.getRequest().getPath().pathWithinApplication().value();
            if (path.equals("/actuator/health") || path.startsWith("/actuator/health/")) {
                return Mono.empty();
            }
            String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
            return apiKey == null
                    ? Mono.empty()
                    : Mono.just(UsernamePasswordAuthenticationToken.unauthenticated("api-key-client", apiKey));
        });
        apiKeyFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        apiKeyFilter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(
                new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(spec -> spec.authenticationEntryPoint(
                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
