package com.zalni.guard

import java.text.Normalizer
import java.util.Locale

/**
 * Pure Kotlin keyword matcher with Unicode NFKC normalization, diacritic stripping,
 * Unicode-aware whole-word boundaries for Latin terms, flexible phrase spacing,
 * and substring containment for non-Latin scripts (e.g. Devanagari/Hindi).
 * Compiles a single cached regular expression per word-list update.
 */
class KeywordMatcher(blockedWords: Set<String>) {

    private val combinedRegex: Regex?

    init {
        val patterns = blockedWords.mapNotNull { rawWord ->
            val normalized = normalize(rawWord)
            if (normalized.isEmpty()) return@mapNotNull null

            val isLatin = isPureLatin(normalized)
            val tokens = normalized.split(" ").filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return@mapNotNull null

            // Build regex allowing flexible whitespace between tokens for multi-word phrases
            val escapedTokens = tokens.joinToString("""\s+""") { Regex.escape(it) }

            if (isLatin) {
                // Whole-word matching using Unicode-aware letter boundaries
                // (?<!\p{L}) ensures not preceded by a letter; (?!\p{L}) ensures not followed by a letter
                """(?<!\p{L})$escapedTokens(?!\p{L})"""
            } else {
                // Non-Latin scripts (e.g. Hindi/Devanagari) match as contains
                escapedTokens
            }
        }

        combinedRegex = if (patterns.isEmpty()) {
            null
        } else {
            Regex(patterns.joinToString("|"), RegexOption.IGNORE_CASE)
        }
    }

    /**
     * Checks if the given text matches any blocked keyword.
     */
    fun matches(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty() || combinedRegex == null) return false
        val normalizedText = normalize(text)
        if (normalizedText.isEmpty()) return false
        return combinedRegex.containsMatchIn(normalizedText)
    }

    companion object {
        /**
         * Normalizes input text:
         * 1. Unicode NFKC canonical decomposition and composition
         * 2. NFD decomposition to separate diacritics
         * 3. Stripping diacritic marks (\p{M})
         * 4. Lowercasing via Locale.ROOT
         * 5. Collapsing consecutive whitespaces into a single space
         */
        fun normalize(input: CharSequence): String {
            val nfkc = Normalizer.normalize(input, Normalizer.Form.NFKC)
            val nfd = Normalizer.normalize(nfkc, Normalizer.Form.NFD)
            val stripped = nfd.replace(Regex("""\p{M}"""), "")
            val lower = stripped.lowercase(Locale.ROOT)
            return lower.replace(Regex("""\s+"""), " ").trim()
        }

        /**
         * Checks whether a word consists only of Latin letters (and non-letters like numbers/punctuation).
         */
        fun isPureLatin(word: String): Boolean {
            return word.all { char ->
                if (!char.isLetter()) {
                    true
                } else {
                    val block = Character.UnicodeBlock.of(char)
                    block == Character.UnicodeBlock.BASIC_LATIN ||
                    block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT ||
                    block == Character.UnicodeBlock.LATIN_EXTENDED_A ||
                    block == Character.UnicodeBlock.LATIN_EXTENDED_B
                }
            }
        }
    }
}
