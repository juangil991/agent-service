package com.vidacruz.agent.infrastructure.adapter.in.cli;

import com.vidacruz.agent.application.service.ProtocolIngestionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.assistant.ingestion.enabled", havingValue = "true")
public class ProtocolIngestionRunner implements ApplicationRunner {

    private final ProtocolIngestionService ingestion;

    public ProtocolIngestionRunner(ProtocolIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        ingestion.ingest().block();
    }
}
