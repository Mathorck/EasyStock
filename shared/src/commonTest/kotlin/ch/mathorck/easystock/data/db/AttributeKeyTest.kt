package ch.mathorck.easystock.data.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttributeKeyTest {
    @Test
    fun slugIsLowercaseAsciiWithUnderscores() {
        assertEquals("breaking_strength", attributeKeySlug("Breaking strength"))
        assertEquals("length", attributeKeySlug("Length"))
        assertEquals("diameter_mm", attributeKeySlug("  Diameter (mm) "))
        assertEquals("a_b", attributeKeySlug("a--__b"))
    }

    @Test
    fun accentsAreFolded() {
        assertEquals("laenge", attributeKeySlug("Länge"))
        assertEquals("resistance", attributeKeySlug("Résistance"))
        assertEquals("groesse", attributeKeySlug("Größe"))
        assertEquals("couleur_a_cote", attributeKeySlug("Couleur à côté"))
    }

    @Test
    fun emptySlugFallsBack() {
        assertEquals(FALLBACK_ATTRIBUTE_KEY, attributeKeySlug(""))
        assertEquals(FALLBACK_ATTRIBUTE_KEY, attributeKeySlug("€ ?!"))
        assertEquals(FALLBACK_ATTRIBUTE_KEY, attributeKeySlug("日本"))
    }

    @Test
    fun slugIsCutAtMaxLength() {
        val slug = attributeKeySlug("word ".repeat(30))
        assertTrue(slug.length <= MAX_ATTRIBUTE_KEY_LENGTH)
        assertTrue(!slug.endsWith("_"))
    }

    @Test
    fun takenKeyGetsASuffix() {
        assertEquals("length", newAttributeKey("Length", emptyList()))
        assertEquals("length_2", newAttributeKey("Length", listOf("length")))
        assertEquals("length_3", newAttributeKey("LENGTH", listOf("length", "length_2")))
    }

    @Test
    fun suffixedKeyStaysWithinMaxLength() {
        val label = "x".repeat(80)
        val base = "x".repeat(MAX_ATTRIBUTE_KEY_LENGTH)
        val key = newAttributeKey(label, listOf(base))
        assertEquals("x".repeat(MAX_ATTRIBUTE_KEY_LENGTH - 2) + "_2", key)
        val keys = (2..12).map { "x".repeat(MAX_ATTRIBUTE_KEY_LENGTH - "_$it".length) + "_$it" } + base
        val next = newAttributeKey(label, keys)
        assertEquals(MAX_ATTRIBUTE_KEY_LENGTH, next.length)
        assertTrue(next.endsWith("_13"))
    }
}
