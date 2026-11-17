package ch.mathorck.easystock.services.push

import ch.mathorck.easystock.enums.push.PushStatus

/** Where the push stands, as the Account screen shows it. */
sealed interface PushUiState {
    /** No distributor app installed: only the 15-minute background sync. */
    data object NoDistributor : PushUiState

    /** Push is off; the user can pick one of [distributors]. */
    data class Off(val distributors: List<PushDistributor>) : PushUiState

    data class On(val distributor: PushDistributor, val status: PushStatus) : PushUiState
}
