package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.server.ApiError

/** The error of a failed action (not of the load), shown once as a message. */
data class ActionFailure(val error: ApiError, val problemType: String? = null)
