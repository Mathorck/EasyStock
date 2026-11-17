package ch.mathorck.easystock.data.model

import ch.mathorck.easystock.enums.article.Tracking

object TrackingSerializer : DbEnumSerializer<Tracking>("Tracking", Tracking.entries.toTypedArray())
