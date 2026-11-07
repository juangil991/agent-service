package com.vidacruz.agent.domain.service;

import com.vidacruz.agent.domain.model.ProtocolSectionChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StructureBasedChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private final int maxCharacters;

    public StructureBasedChunker(int maxCharacters) {
        if (maxCharacters < 1) {
            throw new IllegalArgumentException("maxCharacters debe ser positivo");
        }
        this.maxCharacters = maxCharacters;
    }

    public List<ProtocolSectionChunk> chunk(String markdown, String documentTitle) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<ProtocolSectionChunk> chunks = new ArrayList<>();
        List<Heading> headings = new ArrayList<>();
        StringBuilder sectionContent = new StringBuilder();
        String fallbackSection = nonBlank(documentTitle, "General");
        int[] nextChunkIndex = {0};

        for (String line : markdown.split("\\R")) {
            Matcher heading = HEADING.matcher(line.trim());
            if (heading.matches()) {
                flushSection(chunks, headings, fallbackSection, sectionContent, nextChunkIndex);
                int level = heading.group(1).length();
                while (!headings.isEmpty() && headings.get(headings.size() - 1).level() >= level) {
                    headings.remove(headings.size() - 1);
                }
                headings.add(new Heading(level, heading.group(2).trim()));
            } else {
                sectionContent.append(line).append('\n');
            }
        }
        flushSection(chunks, headings, fallbackSection, sectionContent, nextChunkIndex);
        return List.copyOf(chunks);
    }

    private void flushSection(
            List<ProtocolSectionChunk> chunks,
            List<Heading> headings,
            String fallbackSection,
            StringBuilder sectionContent,
            int[] nextChunkIndex) {
        String content = sectionContent.toString().trim();
        sectionContent.setLength(0);
        if (content.isEmpty()) {
            return;
        }

        String section = headings.size() > 1
                ? headings.subList(1, headings.size()).stream().map(Heading::title).reduce((a, b) -> a + " > " + b)
                    .orElse(fallbackSection)
                : fallbackSection;

        for (String chunkContent : splitContent(content)) {
            chunks.add(new ProtocolSectionChunk(nextChunkIndex[0]++, section, chunkContent));
        }
    }

    private List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\\R\\s*\\R")) {
            String normalized = paragraph.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.length() > maxCharacters) {
                appendParagraphs(chunks, current);
                for (String sentence : splitLongParagraph(normalized)) {
                    appendPart(chunks, current, sentence);
                }
            } else {
                appendPart(chunks, current, normalized);
            }
        }
        appendParagraphs(chunks, current);
        return chunks;
    }

    private void appendPart(List<String> chunks, StringBuilder current, String part) {
        if (!current.isEmpty() && current.length() + 2 + part.length() > maxCharacters) {
            appendParagraphs(chunks, current);
        }
        if (!current.isEmpty()) {
            current.append("\n\n");
        }
        current.append(part);
    }

    private void appendParagraphs(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            String normalized = sentence.trim();
            if (normalized.length() > maxCharacters) {
                appendParagraphs(parts, current);
                splitLongSentence(normalized, parts);
            } else {
                int separatorLength = current.isEmpty() ? 0 : 1;
                if (!current.isEmpty() && current.length() + separatorLength + normalized.length() > maxCharacters) {
                    appendParagraphs(parts, current);
                }
                if (!current.isEmpty()) {
                    current.append(' ');
                }
                current.append(normalized);
            }
        }
        appendParagraphs(parts, current);
        return parts;
    }

    private void splitLongSentence(String sentence, List<String> parts) {
        StringBuilder current = new StringBuilder();
        for (String word : sentence.split("\\s+")) {
            if (!current.isEmpty() && current.length() + 1 + word.length() > maxCharacters) {
                parts.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        appendParagraphs(parts, current);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record Heading(int level, String title) {
    }
}
