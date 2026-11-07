package com.vidacruz.agent.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@WebFluxTest(properties = "app.security.api-key=test-agent-key")
@Import({ApiKeySecurityConfiguration.class, ApiKeySecurityConfigurationTest.ProtectedRoute.class})
class ApiKeySecurityConfigurationTest {

    private static final String API_KEY = "test-agent-key";

    @Autowired
    private WebTestClient client;

    @Test
    void missingApiKeyReturnsUnauthorized() {
        client.get()
                .uri("/test/protected")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void invalidApiKeyReturnsUnauthorized() {
        client.get()
                .uri("/test/protected")
                .header("X-API-Key", "wrong")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validApiKeyAllowsProtectedRequest() {
        client.get()
                .uri("/test/protected")
                .header("X-API-Key", API_KEY)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void metricsRequestRequiresApiKey() {
        client.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validBearerApiKeyAllowsMetricsRequest() {
        client.get()
                .uri("/actuator/prometheus")
                .headers(headers -> headers.setBearerAuth(API_KEY))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void invalidBearerApiKeyReturnsUnauthorizedForMetrics() {
        client.get()
                .uri("/actuator/prometheus")
                .headers(headers -> headers.setBearerAuth("wrong"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void bearerApiKeyDoesNotAuthenticateBusinessRequests() {
        client.get()
                .uri("/test/protected")
                .headers(headers -> headers.setBearerAuth(API_KEY))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void healthPathsDoNotRequireAnApiKey() {
        client.get()
                .uri("/actuator/health/readiness")
                .header("X-API-Key", "wrong")
                .exchange()
                .expectStatus().value(status -> assertNotEquals(401, status));
    }

    @Test
    void missingOrBlankApiKeyFailsConfigurationBinding() {
        propertiesContextRunner().run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
        propertiesContextRunner()
                .withPropertyValues("app.security.api-key= ")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context).hasFailed());
    }

    private ApplicationContextRunner propertiesContextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        ValidationAutoConfiguration.class))
                .withUserConfiguration(ApiKeyPropertiesConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApiKeyProperties.class)
    static class ApiKeyPropertiesConfiguration {
    }

    @RestController
    static class ProtectedRoute {

        @GetMapping("/test/protected")
        Mono<String> protectedRoute() {
            return Mono.just("ok");
        }

        @GetMapping("/actuator/prometheus")
        Mono<String> metricsRoute() {
            return Mono.just("metrics");
        }
    }
}
