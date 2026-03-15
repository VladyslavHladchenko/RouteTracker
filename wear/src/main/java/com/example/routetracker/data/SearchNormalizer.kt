package com.example.routetracker.data

import java.text.Normalizer
import java.util.Locale

object SearchNormalizer {
    private val combiningMarksRegex = "\\p{M}+".toRegex()
    private val separatorsRegex = "[^\\p{Alnum}]+".toRegex()
    private val whitespaceRegex = "\\s+".toRegex()

    /**
     * Normalizes text for watch-sized local search:
     * - strips accents so "Nadrazi" matches "Nádraží"
     * - lowercases using a stable locale
     * - collapses punctuation and repeated whitespace
     */
    fun normalize(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        val withoutMarks = combiningMarksRegex.replace(decomposed, "")
        val lowerCased = withoutMarks.lowercase(Locale.ROOT)
        val separated = separatorsRegex.replace(lowerCased, " ")
        return whitespaceRegex.replace(separated, " ").trim()
    }

    fun tokenize(value: String): List<String> {
        return normalize(value)
            .split(' ')
            .filter { it.isNotBlank() }
    }
}

internal object SearchRanking {
    fun score(
        normalizedQuery: String,
        normalizedCandidate: String,
    ): Int? {
        if (normalizedQuery.isBlank()) {
            return 0
        }
        if (normalizedCandidate.isBlank()) {
            return null
        }
        if (normalizedCandidate == normalizedQuery) {
            return 0
        }
        if (normalizedCandidate.startsWith(normalizedQuery)) {
            return 1
        }

        val queryTokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
        val candidateTokens = normalizedCandidate.split(' ').filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) {
            return 0
        }
        if (queryTokens.all { queryToken -> candidateTokens.any { it.startsWith(queryToken) } }) {
            return 2
        }
        if (queryTokens.all { normalizedCandidate.contains(it) }) {
            return 3
        }
        return null
    }
}
