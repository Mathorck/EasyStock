package ch.mathorck.easystock.enums.review

// ---------- Finish ----------

/** What to do with a unit marked missing. */
enum class MissingResolution {
    /** Status lost, -1 (reason loss), like the "lost" unit action. */
    LOSE,
    /** Nothing changes, the unit stays where the stock says. */
    KEEP,
}
