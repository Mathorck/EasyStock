package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.exceptions.server.ApiException

internal fun Throwable.toFailure(): ActionFailure =
    (this as? ApiException)?.let { ActionFailure(it.error, it.problemType) } ?: ActionFailure(ApiError.SERVER)
