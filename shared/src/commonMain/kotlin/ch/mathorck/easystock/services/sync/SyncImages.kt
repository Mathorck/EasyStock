package ch.mathorck.easystock.services.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Counts the photo downloads, so a placeholder shown for a photo still downloading loads it once it is there. */
object SyncImages {
    private val _downloads = MutableStateFlow(0)
    val downloads: StateFlow<Int> = _downloads.asStateFlow()

    fun downloaded() = _downloads.update { it + 1 }
}
