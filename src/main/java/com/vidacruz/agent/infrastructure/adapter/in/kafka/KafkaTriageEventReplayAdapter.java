package com.vidacruz.agent.infrastructure.adapter.in.kafka;

import com.vidacruz.agent.application.port.out.TriageEventReplayPort;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.kafka.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTriageEventReplayAdapter implements TriageEventReplayPort {

    private static final Logger logger = LoggerFactory.getLogger(KafkaTriageEventReplayAdapter.class);
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_IDLE_POLLS = 60;

    private final String bootstrapServers;
    private final String topic;
    private final TriageEventProjector projector;

    public KafkaTriageEventReplayAdapter(
            @Value("${app.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.kafka.topic}") String topic,
            TriageEventProjector projector) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.projector = projector;
    }

    @Override
    public Mono<Void> replay() {
        return Mono.<Void>fromRunnable(this::replayThroughCapturedOffsets)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void replayThroughCapturedOffsets() {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                consumerProperties(),
                new StringDeserializer(),
                new StringDeserializer())) {
            var topicPartitions = consumer.partitionsFor(topic, METADATA_TIMEOUT);
            if (topicPartitions == null || topicPartitions.isEmpty()) {
                throw new IllegalStateException("El tópico Kafka no tiene particiones: " + topic);
            }
            List<TopicPartition> partitions = topicPartitions
                    .stream()
                    .map(partition -> new TopicPartition(partition.topic(), partition.partition()))
                    .toList();

            consumer.assign(partitions);
            Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions, METADATA_TIMEOUT);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions, METADATA_TIMEOUT);
            for (TopicPartition partition : partitions) {
                if (beginningOffsets.get(partition) != 0L) {
                    throw new IllegalStateException(
                            "Kafka ya no conserva el historial completo de la partición " + partition);
                }
            }

            consumer.seekToBeginning(partitions);
            logger.info(
                    "Starting Kafka projection replay: topic={}, partitions={}, capturedEndOffsets={}",
                    topic,
                    partitions.size(),
                    endOffsets);

            int idlePolls = 0;
            long projectedEvents = 0;
            while (!hasReachedEndOffsets(consumer, partitions, endOffsets)) {
                ConsumerRecords<String, String> records = consumer.poll(POLL_INTERVAL);
                if (records.isEmpty()) {
                    if (++idlePolls >= MAX_IDLE_POLLS) {
                        throw new IllegalStateException("Kafka replay dejó de avanzar en el tópico " + topic);
                    }
                    continue;
                }
                idlePolls = 0;
                for (ConsumerRecord<String, String> record : records) {
                    TopicPartition partition = new TopicPartition(record.topic(), record.partition());
                    if (record.offset() < endOffsets.get(partition)) {
                        projector.project(record.value()).block(EVENT_TIMEOUT);
                        projectedEvents++;
                    }
                }
            }
            logger.info(
                    "Kafka projection replay completed: topic={}, projectedEvents={}",
                    topic,
                    projectedEvents);
        }
    }

    private boolean hasReachedEndOffsets(
            KafkaConsumer<String, String> consumer,
            List<TopicPartition> partitions,
            Map<TopicPartition, Long> endOffsets) {
        return partitions.stream().allMatch(partition -> consumer.position(partition) >= endOffsets.get(partition));
    }

    private Map<String, Object> consumerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "vidacruz-agent-rebuild-" + UUID.randomUUID());
        properties.put(ConsumerConfig.CLIENT_ID_CONFIG, "vidacruz-agent-rebuild-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        return properties;
    }
}
