package com.vidacruz.agent.domain.model;

import java.util.List;

public record AnswerVerification(List<ClaimAssessment> claims) {

    public AnswerVerification {
        if (claims == null || claims.isEmpty()) {
            throw new IllegalArgumentException("La verificación debe evaluar al menos una afirmación");
        }
        claims = List.copyOf(claims);
    }

    public boolean allClaimsSupported() {
        return claims.stream().allMatch(ClaimAssessment::supported);
    }

    public record ClaimAssessment(String claim, boolean supported) {

        public ClaimAssessment {
            if (claim == null || claim.isBlank()) {
                throw new IllegalArgumentException("La afirmación verificada es obligatoria");
            }
        }
    }
}
