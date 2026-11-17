package ch.mathorck.easystock.viewmodels.location

/** One-shot feedback, shown once as a snackbar. */
sealed interface LocationMessage {
    data class HasStock(val name: String) : LocationMessage
    data class Deleted(val count: Int) : LocationMessage
    data object DeleteFailed : LocationMessage
}
