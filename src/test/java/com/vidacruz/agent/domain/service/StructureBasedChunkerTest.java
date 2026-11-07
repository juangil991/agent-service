package com.vidacruz.agent.domain.service;

import com.vidacruz.agent.domain.model.ProtocolSectionChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructureBasedChunkerTest {

    @Test
    void chunksByMarkdownSectionAndKeepsNestedHeadings() {
        StructureBasedChunker chunker = new StructureBasedChunker(500);

        List<ProtocolSectionChunk> chunks = chunker.chunk("""
                # Protocolo de sepsis

                ## Acciones iniciales
                Tomar lactato antes del antibiótico.

                ### Antibióticos
                Administrar antibiótico en la primera hora.
                """, "Protocolo de sepsis");

        assertThat(chunks)
                .extracting(ProtocolSectionChunk::section)
                .containsExactly("Acciones iniciales", "Acciones iniciales > Antibióticos");
        assertThat(chunks).extracting(ProtocolSectionChunk::chunkIndex).containsExactly(0, 1);
    }

    @Test
    void splitsOversizedSectionsWithoutExceedingConfiguredLimit() {
        String first = "Primera oración con información clínica suficiente.";
        String second = "Segunda oración con información clínica suficiente.";
        StructureBasedChunker chunker = new StructureBasedChunker(55);

        List<ProtocolSectionChunk> chunks = chunker.chunk(
                "# Protocolo\n\n## Medidas\n" + first + " " + second,
                "Protocolo");

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content().length()).isLessThanOrEqualTo(55));
        assertThat(chunks).extracting(ProtocolSectionChunk::section).containsOnly("Medidas");
    }
}
