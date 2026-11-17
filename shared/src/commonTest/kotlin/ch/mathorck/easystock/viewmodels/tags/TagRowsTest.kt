package ch.mathorck.easystock.viewmodels.tags

import ch.mathorck.easystock.data.dao.tags.TagTarget
import ch.mathorck.easystock.enums.tags.TagListFilter
import ch.mathorck.easystock.enums.tags.TagListSort
import ch.mathorck.easystock.viewmodels.scan.place
import ch.mathorck.easystock.viewmodels.scan.tagged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagRowsTest {
    private val garage = place("g", "Garage")
    private val shelf = place("s", "Shelf", parentId = "g", barcode = "CAFE0001")
    private val bin = place("b", "Bin", barcode = "0A0B")

    @Test
    fun onlyActiveTaggedRowsAreListedByName() {
        val rows = buildTagRows(
            articles = listOf(
                tagged("a1", "drill", barcode = "DEADBEEF01"),
                tagged("a2", "Untagged"),
                tagged("a3", "Gone", barcode = "11").copy(deletedAt = 5),
            ),
            locations = listOf(garage, shelf, bin, place("x", "Old", barcode = "22").copy(deletedAt = 5)),
        )
        assertEquals(listOf("Bin", "drill", "Shelf"), rows.map { it.name })
        assertEquals(listOf("0A0B", "DEADBEEF01", "CAFE0001"), rows.map { it.tagId })
        assertEquals(TagTarget.OfArticle("a1"), rows[1].target)
        assertEquals(TagTarget.OfLocation("s"), rows[2].target)
    }

    @Test
    fun locationShowsItsPathOnlyWhenNested() {
        val rows = buildTagRows(emptyList(), listOf(garage, shelf, bin))
        assertEquals("Garage › Shelf", rows.single { it.name == "Shelf" }.subtitle)
        assertNull(rows.single { it.name == "Bin" }.subtitle)
        assertTrue(rows.all { it.isLocation })
    }

    @Test
    fun articleShowsSkuPropertiesAndArchived() {
        val rows = buildTagRows(
            listOf(tagged("a", "Drill", barcode = "01", sku = "D-1", archived = true), tagged("b", "Saw", barcode = "02")),
            emptyList(),
        ) { if (it.id == "a") "18 V" else null }
        assertEquals("D-1 · 18 V", rows[0].subtitle)
        assertTrue(rows[0].archived)
        assertNull(rows[1].subtitle)
        assertFalse(rows[1].archived)
    }

    private val sample = listOf(
        TagRow("DEADBEEF01", TagTarget.OfArticle("a"), "Drill"),
        TagRow("CAFE0001", TagTarget.OfLocation("l"), "Caisse cordes"),
        TagRow("0A0B", TagTarget.OfArticle("b"), "adapter"),
    )

    @Test
    fun searchMatchesNameOrTagId() {
        assertEquals(listOf("Drill"), sample.searchTags("dri").map { it.name })
        assertEquals(listOf("Drill"), sample.searchTags("be:ef").map { it.name })
        assertEquals(listOf("Caisse cordes"), sample.searchTags(" cafe ").map { it.name })
        assertEquals(sample, sample.searchTags("  "))
        assertEquals(emptyList(), sample.searchTags("zzz"))
    }

    @Test
    fun filterKeepsOneType() {
        assertEquals(listOf("Drill", "adapter"), sample.filterTags(TagListFilter.ARTICLES).map { it.name })
        assertEquals(listOf("Caisse cordes"), sample.filterTags(TagListFilter.LOCATIONS).map { it.name })
        assertEquals(sample, sample.filterTags(TagListFilter.ALL))
    }

    @Test
    fun sortByNameIgnoresCaseOrByTagId() {
        assertEquals(listOf("adapter", "Caisse cordes", "Drill"), sample.sortTags(TagListSort.NAME).map { it.name })
        assertEquals(listOf("0A0B", "CAFE0001", "DEADBEEF01"), sample.sortTags(TagListSort.TAG_ID).map { it.tagId })
        assertEquals(listOf("0A0B", "DEADBEEF01"), sample.displayedTags("", TagListFilter.ARTICLES, TagListSort.TAG_ID).map { it.tagId })
    }
}
