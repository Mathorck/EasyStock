package ch.mathorck.easystock.exceptions.stock

import ch.mathorck.easystock.enums.article.ItemStatus

/** The unit is not in a status that allows the action (changed meanwhile). */
class InvalidItemStatusException(val status: ItemStatus) : IllegalStateException("Not allowed for status $status")
