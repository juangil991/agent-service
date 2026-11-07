package com.vidacruz.agent.infrastructure.adapter.out.redis;

import com.vidacruz.agent.application.port.out.UrgencyProjectionPort;
import com.vidacruz.agent.domain.model.TriageEvent;
import com.vidacruz.agent.domain.model.TriageLevel;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Repository
public class RedisUrgencyProjectionAdapter implements UrgencyProjectionPort {

    private static final String PATIENTS_KEY = "vidacruz:urgencias:pacientes";
    private static final String COUNTS_KEY = "vidacruz:urgencias:conteos";
    private static final String PROCESSED_EVENTS_KEY = "vidacruz:urgencias:eventos-procesados";

    private static final DefaultRedisScript<Long> APPLY_EVENT = new DefaultRedisScript<>("""
            if redis.call('SISMEMBER', KEYS[3], ARGV[1]) == 1 then
                return 0
            end

            local patientId = ARGV[2]
            local newLevel = ARGV[3]
            local newStatus = ARGV[4]
            local previous = redis.call('HGET', KEYS[1], patientId)

            if previous then
                local oldLevel, oldStatus = string.match(previous, '^([^|]+)|([^|]+)$')
                if (oldStatus == 'ALTA' and newStatus ~= 'ALTA')
                        or (oldStatus == 'EN_CONSULTA' and newStatus == 'REGISTRADO') then
                    redis.call('SADD', KEYS[3], ARGV[1])
                    return 0
                end
                if oldStatus and oldStatus ~= 'ALTA' then
                    redis.call('HINCRBY', KEYS[2], oldLevel, -1)
                end
            end

            if newStatus ~= 'ALTA' then
                redis.call('HINCRBY', KEYS[2], newLevel, 1)
            end

            redis.call('HSET', KEYS[1], patientId, newLevel .. '|' .. newStatus)
            redis.call('SADD', KEYS[3], ARGV[1])
            return 1
            """, Long.class);

    private final ReactiveStringRedisTemplate redis;

    public RedisUrgencyProjectionAdapter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Void> apply(TriageEvent event) {
        return redis.execute(
                        APPLY_EVENT,
                        List.of(PATIENTS_KEY, COUNTS_KEY, PROCESSED_EVENTS_KEY),
                        List.of(
                                event.eventId().toString(),
                                event.patientId().toString(),
                                event.triageLevel().name(),
                                event.status().name()))
                .then();
    }

    @Override
    public Mono<Map<TriageLevel, Long>> findActiveCounts() {
        return redis.<String, String>opsForHash()
                .entries(COUNTS_KEY)
                .collectMap(
                        entry -> TriageLevel.valueOf(entry.getKey()),
                        entry -> Long.parseLong(entry.getValue()));
    }
}
