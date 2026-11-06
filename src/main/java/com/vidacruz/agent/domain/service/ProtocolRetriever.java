package com.vidacruz.agent.domain.service;

import com.vidacruz.agent.domain.model.ProtocolChunk;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProtocolRetriever {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "de", "la", "el", "los", "las", "y", "o", "en", "para", "con",
            "que", "un", "una", "del", "al", "por", "ante", "es", "se");
    private static final int MIN_SCORE = 2;

    public List<ProtocolChunk> retrieve(String question, List<ProtocolChunk> corpus, int limit) {
        Set<String> questionTokens = tokens(question);
        if (questionTokens.isEmpty()) {
            return List.of();
        }
        return corpus.stream()
                .map(chunk -> new ScoredChunk(chunk, score(questionTokens, chunk)))
                .filter(scored -> scored.score >= MIN_SCORE)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                .limit(limit)
                .map(ScoredChunk::chunk)
                .toList();
    }

    private int score(Set<String> questionTokens, ProtocolChunk chunk) {
        Set<String> chunkTokens = tokens(chunk.contenido() + " " + chunk.protocolo() + " " + chunk.seccion());
        int overlap = 0;
        for (String token : questionTokens) {
            if (chunkTokens.contains(token)) {
                overlap++;
            }
        }
        return overlap;
    }

    private Set<String> tokens(String value) {
        return SPLIT_PATTERN.splitAsStream(normalize(value))
                .map(String::trim)
                .filter(token -> token.length() >= 3)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        String nonNull = value == null ? "" : value;
        String normalized = Normalizer.normalize(nonNull, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private record ScoredChunk(ProtocolChunk chunk, int score) {
    }
}

