package com.vidacruz.agent.integration;

import com.vidacruz.agent.domain.model.EmbeddedProtocolChunk;
import com.vidacruz.agent.domain.model.ProtocolChunk;
import com.vidacruz.agent.domain.model.ProtocolDocument;
import com.vidacruz.agent.infrastructure.adapter.out.persistence.PostgresProtocolVectorStoreAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PostgresProtocolVectorStoreIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("paradedb/paradedb:0.26.5").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agent_test")
            .withUsername("agent_test")
            .withPassword("agent_test");

    private static DatabaseClient databaseClient;
    private static PostgresProtocolVectorStoreAdapter vectorStore;

    @BeforeAll
    static void startPostgresAndInitializeSchema() throws IOException {
        ConnectionFactory connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(DRIVER, "postgresql")
                .option(HOST, POSTGRES.getHost())
                .option(PORT, POSTGRES.getMappedPort(5432))
                .option(DATABASE, POSTGRES.getDatabaseName())
                .option(USER, POSTGRES.getUsername())
                .option(PASSWORD, POSTGRES.getPassword())
                .build());
        databaseClient = DatabaseClient.create(connectionFactory);
        vectorStore = new PostgresProtocolVectorStoreAdapter(
                databaseClient,
                TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory)));

        String schema = new ClassPathResource("schema.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Flux.fromArray(schema.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .concatMap(statement -> databaseClient.sql(statement).then())
                .blockLast(Duration.ofSeconds(30));
    }

    @BeforeEach
    void clearDocuments() {
        databaseClient.sql("TRUNCATE TABLE protocol_chunk, protocol_document")
                .then()
                .block(Duration.ofSeconds(10));
    }

    @Test
    void replacesDocumentAndSearchesItsPersistedVectorChunks() {
        ProtocolDocument document = document();
        List<EmbeddedProtocolChunk> firstVersion = List.of(chunk("crisis-0", 0,
                "La crisis hipertensiva requiere valoración clínica inmediata."));

        vectorStore.replaceDocument(document, "hash-v1", firstVersion).block(Duration.ofSeconds(10));

        assertThat(vectorStore.isDocumentCurrent("PROTO-TEST", "hash-v1").block(Duration.ofSeconds(5))).isTrue();
        assertThat(vectorStore.isDocumentCurrent("PROTO-TEST", "different-hash").block(Duration.ofSeconds(5))).isFalse();
        assertThat(vectorStore.search(
                        "crisis hipertensiva",
                        embedding(),
                        20,
                        3,
                        60,
                        0.55)
                .block(Duration.ofSeconds(10)))
                .containsExactly(new ProtocolChunk(
                        "PROTO-TEST",
                        "Evaluación",
                        "La crisis hipertensiva requiere valoración clínica inmediata."));

        vectorStore.replaceDocument(document, "hash-v2", List.of(chunk("crisis-1", 0,
                "La crisis hipertensiva requiere monitorización continua.")))
                .block(Duration.ofSeconds(10));

        assertThat(vectorStore.isDocumentCurrent("PROTO-TEST", "hash-v1").block(Duration.ofSeconds(5))).isFalse();
        assertThat(vectorStore.isDocumentCurrent("PROTO-TEST", "hash-v2").block(Duration.ofSeconds(5))).isTrue();
        assertThat(vectorStore.search(
                        "crisis hipertensiva",
                        embedding(),
                        20,
                        3,
                        60,
                        0.55)
                .block(Duration.ofSeconds(10)))
                .containsExactly(new ProtocolChunk(
                        "PROTO-TEST",
                        "Evaluación",
                        "La crisis hipertensiva requiere monitorización continua."));
    }

    @Test
    void rollsBackChunkDeletionWhenReplacementInsertFails() {
        ProtocolDocument document = document();
        vectorStore.replaceDocument(
                        document,
                        "hash-v1",
                        List.of(chunk("stable-chunk", 0, "Contenido original de crisis hipertensiva.")))
                .block(Duration.ofSeconds(10));

        List<EmbeddedProtocolChunk> invalidReplacement = List.of(
                chunk("duplicate-chunk", 0, "Primer fragmento."),
                chunk("duplicate-chunk", 1, "Segundo fragmento."));

        assertThatThrownBy(() -> vectorStore.replaceDocument(document, "hash-v2", invalidReplacement)
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(RuntimeException.class);

        assertThat(vectorStore.isDocumentCurrent("PROTO-TEST", "hash-v1").block(Duration.ofSeconds(5))).isTrue();
        assertThat(vectorStore.search(
                        "crisis hipertensiva",
                        embedding(),
                        20,
                        3,
                        60,
                        0.55)
                .block(Duration.ofSeconds(10)))
                .containsExactly(new ProtocolChunk(
                        "PROTO-TEST",
                        "Evaluación",
                        "Contenido original de crisis hipertensiva."));
    }

    private ProtocolDocument document() {
        return new ProtocolDocument("PROTO-TEST", "PROTO-TEST", "Protocolo de prueba", "1", "test", "");
    }

    private EmbeddedProtocolChunk chunk(String id, int index, String content) {
        return new EmbeddedProtocolChunk(id, "PROTO-TEST", "PROTO-TEST", "Evaluación", index, content, embedding());
    }

    private List<Double> embedding() {
        List<Double> values = new ArrayList<>(1024);
        values.add(1.0);
        for (int index = 1; index < 1024; index++) {
            values.add(0.0);
        }
        return values;
    }
}
