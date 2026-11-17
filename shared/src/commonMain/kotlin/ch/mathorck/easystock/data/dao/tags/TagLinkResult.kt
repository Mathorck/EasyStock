package ch.mathorck.easystock.data.dao.tags

/** Result of [TagDao.linkTag]. */
sealed interface TagLinkResult {
    /** The tag is now on the target (also when it already was). */
    data object Linked : TagLinkResult

    /** Another active article or location holds the tag: nothing changed. Ask the user, then call again with it. */
    data class LinkedElsewhere(val owner: TagOwner) : TagLinkResult

    /** The target is deleted (or never existed): nothing changed. */
    data object TargetGone : TagLinkResult
}
