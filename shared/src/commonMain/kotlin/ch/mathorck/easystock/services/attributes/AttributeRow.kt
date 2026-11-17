package ch.mathorck.easystock.services.attributes

import ch.mathorck.easystock.composables.attributes.label

/** One attribute of an article for the detail view: its [label] and the formatted [value] ("50 m", "✓" for a bool). */
data class AttributeRow(val key: String, val label: String, val value: String)
