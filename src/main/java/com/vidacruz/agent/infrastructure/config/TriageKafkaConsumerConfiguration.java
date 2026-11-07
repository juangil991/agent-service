package com.vidacruz.agent.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "app.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class TriageKafkaConsumerConfiguration {

    @Bean
    KafkaReceiver<String, String> triageKafkaReceiver(
            @Value("${app.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.kafka.topic}") String topic,
            @Value("${app.kafka.consumer.group-id}") String groupId) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ReceiverOptions<String, String> receiverOptions = ReceiverOptions.<String, String>create(properties)
                .subscription(List.of(topic));
        return KafkaReceiver.create(receiverOptions);
    }
}
