package com.vidacruz.agent.domain.service;

import com.vidacruz.agent.domain.model.ProtocolChunk;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtocolEvidenceRelevance {

    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Set<String> STOP_WORDS = Set.of(
            "ante", "como", "con", "cual", "cuales", "cuando", "de", "del", "desde", "donde",
            "el", "ella", "ellas", "ellos", "en", "entre", "es", "esta", "este", "esto", "hay",
            "la", "las", "lo", "los", "mas", "mi", "mis", "para", "por", "que", "quien",
            "se", "sin", "sobre", "son", "su", "sus", "un", "una", "uno", "unos", "unas",
            "y", "o", "hora");

    public boolean isRelevant(String question, ProtocolChunk chunk) {
        Set<String> queryTerms = terms(question);
        if (queryTerms.isEmpty()) {
            return false;
        }

        Set<String> evidenceTerms = terms(
                chunk.titulo() + " " + chunk.seccion() + " " + chunk.contenido());
        long matchingTerms = queryTerms.stream().filter(evidenceTerms::contains).count();
        return matchingTerms >= Math.min(2, queryTerms.size());
    }

    private Set<String> terms(String text) {
        String normalized = DIACRITICS.matcher(
                Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        Matcher matcher = WORDS.matcher(normalized);
        Set<String> terms = new HashSet<>();
        while (matcher.find()) {
            String term = normalizeTerm(matcher.group());
            if (term.length() >= 4 && !STOP_WORDS.contains(term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    private String normalizeTerm(String word) {
        if (word.startsWith("primer")) {
            return "prim";
        }
        return word;
    }
}
