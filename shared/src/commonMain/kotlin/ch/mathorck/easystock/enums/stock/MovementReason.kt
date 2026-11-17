package ch.mathorck.easystock.enums.stock

import ch.mathorck.easystock.data.model.MovementReasonSerializer
import kotlinx.serialization.Serializable

@Serializable(with = MovementReasonSerializer::class)
enum class MovementReason { PURCHASE, CONSUMPTION, INVENTORY, TRANSFER, LOAN, RETURN, LOSS }
