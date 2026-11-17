package ch.mathorck.easystock.services.alerts

/** A notification still shown on the phone: [key] is the article id, or [GROUP_KEY]. */
data class ShownAlert(val key: String, val articleIds: Set<String>)
