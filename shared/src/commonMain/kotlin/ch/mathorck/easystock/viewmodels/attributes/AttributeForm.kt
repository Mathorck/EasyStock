package ch.mathorck.easystock.viewmodels.attributes

import ch.mathorck.easystock.enums.attributes.AttributeDataType

/** Raw input of the attribute dialog. [optionsText] holds one enum option per line. */
data class AttributeForm(
    val label: String = "",
    val dataType: AttributeDataType = AttributeDataType.TEXT,
    val unit: String = "",
    val optionsText: String = "",
    /** Date only: flag the articles whose date is past. */
    val flagWhenPast: Boolean = false,
    /** Date only, needs [flagWhenPast]: notify once when a date is past. */
    val notifyWhenPast: Boolean = false,
)
