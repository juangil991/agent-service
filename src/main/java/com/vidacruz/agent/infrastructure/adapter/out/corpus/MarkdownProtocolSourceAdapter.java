package com.vidacruz.agent.infrastructure.adapter.out.corpus;

import com.vidacruz.agent.application.port.out.ProtocolSourcePort;
import com.vidacruz.agent.domain.model.ProtocolDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class MarkdownProtocolSourceAdapter implements ProtocolSourcePort {

    private final Path directory;

    public MarkdownProtocolSourceAdapter(
            @Value("${app.assistant.protocols.directory:./protocols}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public Mono<List<ProtocolDocument>> loadDocuments() {
        return Mono.fromCallable(this::readDocuments)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<ProtocolDocument> readDocuments() throws IOException {
        if (Files.notExists(directory)) {
            return List.of();
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException("La ruta de protocolos no es un directorio: " + directory);
        }

        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::readDocument)
                    .toList();
        }
    }

    private ProtocolDocument readDocument(Path path) {
        try {
            String filename = path.getFileName().toString();
            String protocolId = filename.substring(0, filename.length() - 3);
            String markdown = Files.readString(path);
            String title = markdown.lines()
                    .filter(line -> line.startsWith("# "))
                    .map(line -> line.substring(2).trim())
                    .findFirst()
                    .orElse(protocolId);
            String relativePath = directory.relativize(path.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
            return new ProtocolDocument(
                    "markdown:" + relativePath,
                    protocolId,
                    title,
                    "",
                    relativePath,
                    markdown);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException("No se pudo leer el protocolo Markdown: " + path, exception);
        }
    }
}
