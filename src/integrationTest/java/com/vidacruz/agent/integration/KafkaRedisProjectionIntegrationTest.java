package com.vidacruz.agent.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidacruz.agent.application.service.GetUrgencyStatusService;
import com.vidacruz.agent.application.service.ProjectTriageEventService;
import com.vidacruz.agent.application.service.RedisProjectionRecoveryService;
import com.vidacruz.agent.application.port.out.TriageEventReplayPort;
import com.vidacruz.agent.domain.model.TriageLevel;
import com.vidacruz.agent.infrastructure.adapter.in.kafka.KafkaTriageEventReplayAdapter;
import com.vidacruz.agent.infrastructure.adapter.in.kafka.TriageEventConsumer;
import com.vidacruz.agent.infrastructure.adapter.in.kafka.TriageEventProjector;
import com.vidacruz.agent.infrastructure.adapter.out.redis.RedisUrgencyProjectionAdapter;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class KafkaRedisProjectionIntegrationTest {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory redisConnectionFactory;
    private static ReactiveStringRedisTemplate redis;
    private static GetUrgencyStatusService statusService;
    private static ObjectMapper objectMapper;

    private KafkaProducer<String, String> producer;
    private TriageEventConsumer consumer;
    private RedisUrgencyProjectionAdapter projection;
    private String topic;
    private String consumerGroupId;

    @BeforeAll
    static void configureRedis() {
        redisConnectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379));
        redisConnectionFactory.afterPropertiesSet();
        redisConnectionFactory.start();
        redis = new ReactiveStringRedisTemplate(redisConnectionFactory);
        statusService = new GetUrgencyStatusService(new RedisUrgencyProjectionAdapter(redis));
        objectMapper = new ObjectMapper();
    }

    @AfterAll
    static void stopRedisConnectionFactory() {
        redisConnectionFactory.destroy();
    }

    @BeforeEach
    void startProducerAndConsumer() throws Exception {
        topic = "agent-projection-" + UUID.randomUUID();
        consumerGroupId = "agent-projection-" + UUID.randomUUID();
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        }

        producer = new KafkaProducer<>(producerProperties());
        ReceiverOptions<String, String> receiverOptions = ReceiverOptions.<String, String>create(consumerProperties())
                .subscription(List.of(topic));
        projection = new RedisUrgencyProjectionAdapter(redis);
        consumer = new TriageEventConsumer(
                KafkaReceiver.create(receiverOptions),
                new TriageEventProjector(objectMapper, new ProjectTriageEventService(projection)));
        consumer.start();
    }

    @AfterEach
    void stopConsumerAndProducer() {
        if (consumer != null) {
            consumer.stop();
        }
        if (producer != null) {
            producer.close(Duration.ofSeconds(5));
        }
        flushRedis();
    }

    @Test
    void projectsKafkaEventsAtomicallyAndDeduplicatesEventIdsInRedis() throws Exception {
        UUID patientId = UUID.randomUUID();
        UUID registeredEventId = UUID.randomUUID();
        send(registeredEventId, patientId, "paciente_registrado", "URGENTE", "REGISTRADO");
        awaitCount(TriageLevel.URGENTE, 1);

        send(registeredEventId, patientId, "paciente_registrado", "URGENTE", "REGISTRADO");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(count(TriageLevel.URGENTE)).isEqualTo(1));

        send(UUID.randomUUID(), patientId, "paciente_en_consulta", "URGENTE", "EN_CONSULTA");
        awaitCount(TriageLevel.URGENTE, 1);

        send(UUID.randomUUID(), patientId, "paciente_alta", "URGENTE", "ALTA");
        awaitCount(TriageLevel.URGENTE, 0);

        send(UUID.randomUUID(), patientId, "paciente_registrado", "URGENTE", "REGISTRADO");
        send(UUID.randomUUID(), UUID.randomUUID(), "paciente_registrado", "URGENTE", "REGISTRADO");
        awaitCount(TriageLevel.URGENTE, 1);
        assertThat(count(TriageLevel.URGENTE)).isEqualTo(1);
    }

    @Test
    void rebuildsRedisFromRetainedEventsWithoutChangingNormalConsumerOffsets() throws Exception {
        UUID patientId = UUID.randomUUID();
        send(UUID.randomUUID(), patientId, "paciente_registrado", "CRITICO", "REGISTRADO");
        awaitCount(TriageLevel.CRITICO, 1);
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(normalConsumerOffsets()).isNotEmpty());
        Map<TopicPartition, OffsetAndMetadata> offsetsBeforeReplay = normalConsumerOffsets();
        assertThat(offsetsBeforeReplay.values()).extracting(OffsetAndMetadata::offset).containsExactly(1L);

        consumer.stop();
        consumer = null;
        flushRedis();
        assertThat(count(TriageLevel.CRITICO)).isZero();

        RedisProjectionRecoveryService recovery = recoveryService(
                new KafkaTriageEventReplayAdapter(KAFKA.getBootstrapServers(), topic,
                        new TriageEventProjector(objectMapper, new ProjectTriageEventService(projection))));
        recovery.recoverIfNeeded().block(Duration.ofSeconds(30));

        assertThat(projection.isComplete().block(Duration.ofSeconds(5))).isTrue();
        assertThat(count(TriageLevel.CRITICO)).isEqualTo(1);
        assertThat(normalConsumerOffsets()).isEqualTo(offsetsBeforeReplay);
    }

    @Test
    void marksAnEmptyKafkaReplayCompleteOnFirstStartup() {
        RedisProjectionRecoveryService recovery = recoveryService(
                new KafkaTriageEventReplayAdapter(KAFKA.getBootstrapServers(), topic,
                        new TriageEventProjector(objectMapper, new ProjectTriageEventService(projection))));

        recovery.recoverIfNeeded().block(Duration.ofSeconds(30));

        assertThat(projection.isComplete().block(Duration.ofSeconds(5))).isTrue();
        assertThat(count(TriageLevel.CRITICO)).isZero();
    }

    @Test
    void skipsReplayWhenProjectionIsAlreadyMarkedComplete() {
        projection.markComplete().block(Duration.ofSeconds(5));
        AtomicInteger replayAttempts = new AtomicInteger();
        RedisProjectionRecoveryService recovery = recoveryService(() -> {
            replayAttempts.incrementAndGet();
            return Mono.error(new IllegalStateException("replay should not be called"));
        });

        recovery.recoverIfNeeded().block(Duration.ofSeconds(5));

        assertThat(replayAttempts).hasValue(0);
        assertThat(projection.isComplete().block(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void clearsPartialProjectionAndRetriesAfterReplayFailure() throws Exception {
        consumer.stop();
        consumer = null;
        UUID patientId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String eventJson = eventJson(eventId, patientId, "paciente_registrado", "URGENTE", "REGISTRADO");
        producer.send(new ProducerRecord<>(topic, eventId.toString(), eventJson))
                .get(10, TimeUnit.SECONDS);

        TriageEventProjector eventProjector = new TriageEventProjector(
                objectMapper,
                new ProjectTriageEventService(projection));
        TriageEventReplayPort kafkaReplay = new KafkaTriageEventReplayAdapter(
                KAFKA.getBootstrapServers(),
                topic,
                eventProjector);
        AtomicInteger attempts = new AtomicInteger();
        TriageEventReplayPort failAfterPartialProjection = () -> Mono.defer(() -> {
            if (attempts.incrementAndGet() == 1) {
                return eventProjector.project(eventJson)
                        .then(projection.isComplete())
                        .flatMap(complete -> {
                            assertThat(complete).isFalse();
                            return Mono.error(new IllegalStateException("simulated replay failure"));
                        });
            }
            return statusService.getActiveCounts()
                    .flatMap(counts -> {
                        assertThat(counts.get(TriageLevel.URGENTE)).isZero();
                        return kafkaReplay.replay();
                    });
        });

        recoveryService(failAfterPartialProjection)
                .recoverIfNeeded()
                .block(Duration.ofSeconds(20));

        assertThat(attempts).hasValue(2);
        assertThat(projection.isComplete().block(Duration.ofSeconds(5))).isTrue();
        assertThat(count(TriageLevel.URGENTE)).isEqualTo(1);
    }

    private Map<String, Object> producerProperties() {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all");
    }

    private Map<String, Object> consumerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return properties;
    }

    private void send(
            UUID eventId,
            UUID patientId,
            String eventType,
            String triageLevel,
            String status) throws Exception {
        producer.send(new ProducerRecord<>(topic, eventId.toString(), eventJson(
                eventId, patientId, eventType, triageLevel, status)))
                .get(10, TimeUnit.SECONDS);
    }

    private String eventJson(
            UUID eventId,
            UUID patientId,
            String eventType,
            String triageLevel,
            String status) throws Exception {
        Map<String, Object> message = Map.of(
                "eventId", eventId.toString(),
                "eventType", eventType,
                "correlationId", UUID.randomUUID().toString(),
                "patientId", patientId.toString(),
                "payload", Map.of("triageLevel", triageLevel, "status", status));
        return objectMapper.writeValueAsString(message);
    }

    private RedisProjectionRecoveryService recoveryService(TriageEventReplayPort replayPort) {
        return new RedisProjectionRecoveryService(projection, replayPort);
    }

    private Map<TopicPartition, OffsetAndMetadata> normalConsumerOffsets() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            return admin.listConsumerGroupOffsets(consumerGroupId)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private void flushRedis() {
        ((ReactiveRedisConnection) redisConnectionFactory.getReactiveConnection())
                .serverCommands()
                .flushDb()
                .block(Duration.ofSeconds(5));
    }

    private void awaitCount(TriageLevel level, long expected) {
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(count(level)).isEqualTo(expected));
    }

    private long count(TriageLevel level) {
        return statusService.getActiveCounts()
                .block(Duration.ofSeconds(3))
                .get(level);
    }
}
