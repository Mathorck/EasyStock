package ch.mathorck.easystock.viewmodels.tags

import ch.mathorck.easystock.data.dao.tags.TagTarget

/** One assigned NFC tag: the article or location holding it. */
data class TagRow(
    /** Stored format ([ch.mathorck.easystock.services.nfc.tagIdOf]); shown with [ch.mathorck.easystock.services.nfc.formatTagId]. */
    val tagId: String,
    val target: TagTarget,
    val name: String,
    /** The location path ("Garage › Shelf"), or the SKU and properties of the article. Null when there is nothing more to say. */
    val subtitle: String? = null,
    /** Location icon and color keys (null for articles, or the default). */
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean = false,
) {
    val isLocation: Boolean get() = target is TagTarget.OfLocation
}
