package ch.mathorck.easystock.viewmodels.stock

import ch.mathorck.easystock.viewmodels.article.foldCase

/**
 * Serial numbers indexed ignoring case, built once per database change so a check per keystroke is a lookup.
 * Duplicates are counted (a unit keeping its own serial removes one).
 */
class SerialIndex private constructor(private val base: SerialIndex?, serials: Collection<String>) {
    constructor(serials: Collection<String>) : this(null, serials)

    private val counts: Map<String, Int> = serials.groupingBy { it.foldCase() }.eachCount()

    private fun count(key: String): Int = (base?.count(key) ?: 0) + (counts[key] ?: 0)

    /** True when [value] is taken (ignoring case), [ownSerial] (the edited unit's stored serial) counted once less. */
    fun isTaken(value: String, ownSerial: String? = null): Boolean {
        val key = value.foldCase()
        val own = if (ownSerial != null && ownSerial.foldCase() == key) 1 else 0
        return count(key) - own > 0
    }

    /** This index with [more] serials added, without indexing this one again (only [more] is). */
    operator fun plus(more: Collection<String>): SerialIndex = if (more.isEmpty()) this else SerialIndex(this, more)

    companion object {
        val EMPTY = SerialIndex(emptyList())
    }
}
