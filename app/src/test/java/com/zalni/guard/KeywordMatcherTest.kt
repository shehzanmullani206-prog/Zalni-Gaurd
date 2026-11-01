package com.zalni.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for KeywordMatcher with the 6 required validation cases.
 */
class KeywordMatcherTest {

    // 1. Whole-word Latin match
    @Test
    fun testWholeWordLatinMatch() {
        val matcher = KeywordMatcher(setOf("sex"))
        assertTrue(matcher.matches("This sentence mentions sex."))
        assertTrue(matcher.matches("sex"))
        assertTrue(matcher.matches("Warning: (sex) content"))
    }

    // 2. Substring Latin word does not match (e.g. "sex" does not match "Essex")
    @Test
    fun testSubstringLatinNoMatch() {
        val matcher = KeywordMatcher(setOf("sex"))
        assertFalse(matcher.matches("Welcome to Essex County"))
        assertFalse(matcher.matches("Middlesex"))
        assertFalse(matcher.matches("unisexual"))
    }

    // 3. Diacritic stripping and NFKC normalization
    @Test
    fun testDiacriticStrippingAndNormalization() {
        val matcher = KeywordMatcher(setOf("cafe"))
        assertTrue(matcher.matches("Let's go to the café downtown"))

        val matcherAccents = KeywordMatcher(setOf("naïve"))
        assertTrue(matcherAccents.matches("He is very naive"))
    }

    // 4. Multi-word phrase with flexible whitespace
    @Test
    fun testMultiWordPhraseWithFlexibleSpaces() {
        val matcher = KeywordMatcher(setOf("bad word"))
        assertTrue(matcher.matches("This is a bad word"))
        assertTrue(matcher.matches("This is a bad    word with multiple spaces"))
        assertFalse(matcher.matches("This is a bad and word separated by other words"))
    }

    // 5. Non-Latin / Devanagari script substring match
    @Test
    fun testNonLatinDevanagariSubstringMatch() {
        // "हिन्दी" (Hindi) in Devanagari script
        val matcher = KeywordMatcher(setOf("हिन्दी"))
        assertTrue(matcher.matches("यहाँ हिन्दी पाठ है"))
        assertTrue(matcher.matches("कुछहिन्दीसामग्री")) // Matches as contains
        assertFalse(matcher.matches("यहाँ केवल अंग्रेज़ी है"))
    }

    // 6. Empty word list or clean text returns false
    @Test
    fun testEmptyWordListOrNoMatchReturnsFalse() {
        val emptyMatcher = KeywordMatcher(emptySet())
        assertFalse(emptyMatcher.matches("Safe innocent text"))

        val activeMatcher = KeywordMatcher(setOf("gambling", "casino"))
        assertFalse(activeMatcher.matches("Reading an educational book about mathematics."))
        assertFalse(activeMatcher.matches(null))
        assertFalse(activeMatcher.matches(""))
    }
}
