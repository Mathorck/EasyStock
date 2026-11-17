package ch.mathorck.easystock.viewmodels.tags

import ch.mathorck.easystock.data.dao.tags.TagOwner
import ch.mathorck.easystock.data.dao.tags.TagTarget

/** Linking asks first: [owner] already holds [tagId], linking moves it to [target]. */
data class TagMoveRequest(val tagId: String, val target: TagTarget, val owner: TagOwner, val targetName: String = "")
