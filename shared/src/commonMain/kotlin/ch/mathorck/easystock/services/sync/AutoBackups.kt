package ch.mathorck.easystock.services.sync

/** The automatic backups written before a reset wipe: only the newest [KEEP] stay. */
object AutoBackups {
    const val KEEP = 3
    const val PREFIX = "auto-"

    /** Of [names] (file names starting with [PREFIX], then a sortable UTC time stamp), those to delete. */
    fun toDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { it.startsWith(PREFIX) }.sortedDescending().drop(keep)
}
