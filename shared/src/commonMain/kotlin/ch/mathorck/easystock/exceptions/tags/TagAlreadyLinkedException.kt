package ch.mathorck.easystock.exceptions.tags

/** The tag is already linked to an active article or location. */
class TagAlreadyLinkedException(val tagId: String) : IllegalStateException("Tag already linked: $tagId")
