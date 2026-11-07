package com.vidacruz.agent.infrastructure.adapter.in.rest;

import com.vidacruz.agent.domain.model.TriageLevel;

import java.util.Map;
import java.util.stream.Collectors;

public record UrgencyStatusResponse(Map<String, Long> pacientesActivosPorNivel) {

    static UrgencyStatusResponse from(Map<TriageLevel, Long> counts) {
        return new UrgencyStatusResponse(counts.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().name(),
                        Map.Entry::getValue)));
    }
}
