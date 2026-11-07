package com.vidacruz.agent.infrastructure.adapter.out.corpus;

import com.vidacruz.agent.domain.model.ProtocolDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownProtocolSourceAdapterTest {

    @TempDir
    Path directory;

    @Test
    void loadsMarkdownFileUsingItsFilenameAsProtocolId() throws IOException {
        Files.writeString(directory.resolve("PROTO-004.md"), """
                # Dolor torácico

                ## Evaluación
                Valorar signos de alarma.
                """);

        ProtocolDocument document = new MarkdownProtocolSourceAdapter(directory.toString())
                .loadDocuments()
                .block()
                .get(0);

        assertThat(document.protocolId()).isEqualTo("PROTO-004");
        assertThat(document.title()).isEqualTo("Dolor torácico");
        assertThat(document.source()).isEqualTo("PROTO-004.md");
        assertThat(document.markdown()).contains("## Evaluación");
    }
}
