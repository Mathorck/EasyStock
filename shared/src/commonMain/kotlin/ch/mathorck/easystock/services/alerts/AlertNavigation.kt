package ch.mathorck.easystock.services.alerts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The target of the last tapped notification, until the Stock page opens it. */
object AlertNavigation {
    private val _target = MutableStateFlow<AlertTarget?>(null)
    val target: StateFlow<AlertTarget?> = _target.asStateFlow()

    fun open(target: AlertTarget) {
        _target.value = target
    }

    fun consumed() {
        _target.value = null
    }
}
