package com.vidacruz.agent.infrastructure.adapter.in.kafka;

import com.vidacruz.agent.application.service.RedisProjectionRecoveryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
class RedisProjectionRecoveryRunner implements ApplicationRunner {

    private final RedisProjectionRecoveryService recovery;

    RedisProjectionRecoveryRunner(RedisProjectionRecoveryService recovery) {
        this.recovery = recovery;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        recovery.recoverIfNeeded().block();
    }
}
