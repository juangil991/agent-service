package com.vidacruz.agent.domain.service;

import com.vidacruz.agent.domain.model.ProtocolChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolRetrieverTest {

    private final ProtocolRetriever retriever = new ProtocolRetriever();

    @Test
    void retrievesRelevantChunksForHypertensiveCrisisQuestion() {
        List<ProtocolChunk> corpus = List.of(
                new ProtocolChunk("PROTO-001", "Medidas iniciales", "Reconfirmar la presión tras 5 minutos de reposo."),
                new ProtocolChunk("PROTO-002", "Acciones iniciales", "Antibiótico de amplio espectro en la primera hora."));

        List<ProtocolChunk> result = retriever.retrieve(
                "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?", corpus, 3);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).protocolo()).isEqualTo("PROTO-001");
    }

    @Test
    void returnsEmptyWhenNoProtocolMatches() {
        List<ProtocolChunk> corpus = List.of(
                new ProtocolChunk("PROTO-001", "Medidas iniciales", "Reconfirmar la presión tras 5 minutos de reposo."));

        List<ProtocolChunk> result = retriever.retrieve(
                "¿Tratamiento de primera línea para diabetes tipo 2?", corpus, 3);

        assertThat(result).isEmpty();
    }
}

