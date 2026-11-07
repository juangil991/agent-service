package com.vidacruz.agent.infrastructure.adapter.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.agent.application.exception.LlmUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiOpenAiCompatibleLlmAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesClaimByClaimVerification() {
        var result = SpringAiOpenAiCompatibleLlmAdapter.parseVerification("""
                {"claims":[
                  {"claim":"Administrar antibiótico durante la primera hora","supported":true},
                  {"claim":"Agregar corticoides","supported":false}
                ]}
                """, objectMapper);

        assertThat(result.claims()).hasSize(2);
        assertThat(result.allClaimsSupported()).isFalse();
    }

    @Test
    void rejectsMalformedOrEmptyVerification() {
        assertThatThrownBy(() -> SpringAiOpenAiCompatibleLlmAdapter.parseVerification(
                "{\"claims\":[{\"claim\":\"afirmación\"}]}", objectMapper))
                .isInstanceOf(LlmUnavailableException.class);
        assertThatThrownBy(() -> SpringAiOpenAiCompatibleLlmAdapter.parseVerification(
                "{\"claims\":[]}", objectMapper))
                .isInstanceOf(LlmUnavailableException.class);
        assertThatThrownBy(() -> SpringAiOpenAiCompatibleLlmAdapter.parseVerification(
                "no es JSON", objectMapper))
                .isInstanceOf(LlmUnavailableException.class);
    }
}
