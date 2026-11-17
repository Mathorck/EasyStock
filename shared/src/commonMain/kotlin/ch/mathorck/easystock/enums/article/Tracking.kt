package ch.mathorck.easystock.enums.article

import ch.mathorck.easystock.data.model.TrackingSerializer
import kotlinx.serialization.Serializable

@Serializable(with = TrackingSerializer::class)
enum class Tracking { QUANTITY, SERIAL }
