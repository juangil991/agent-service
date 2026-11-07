package com.vidacruz.agent.application.service;

import com.vidacruz.agent.application.port.in.GetUrgencyStatusUseCase;
import com.vidacruz.agent.application.port.out.UrgencyProjectionPort;
import com.vidacruz.agent.domain.model.TriageLevel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.EnumMap;
import java.util.Map;

@Service
public class GetUrgencyStatusService implements GetUrgencyStatusUseCase {

    private final UrgencyProjectionPort projection;

    public GetUrgencyStatusService(UrgencyProjectionPort projection) {
        this.projection = projection;
    }

    @Override
    public Mono<Map<TriageLevel, Long>> getActiveCounts() {
        return projection.findActiveCounts()
                .map(counts -> {
                    Map<TriageLevel, Long> completeCounts = new EnumMap<>(TriageLevel.class);
                    for (TriageLevel level : TriageLevel.values()) {
                        completeCounts.put(level, counts.getOrDefault(level, 0L));
                    }
                    return Map.copyOf(completeCounts);
                });
    }
}
