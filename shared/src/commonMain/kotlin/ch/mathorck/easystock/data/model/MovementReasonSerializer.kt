package ch.mathorck.easystock.data.model

import ch.mathorck.easystock.enums.stock.MovementReason

object MovementReasonSerializer : DbEnumSerializer<MovementReason>("MovementReason", MovementReason.entries.toTypedArray())
