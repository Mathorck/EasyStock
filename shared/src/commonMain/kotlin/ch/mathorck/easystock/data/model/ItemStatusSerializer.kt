package ch.mathorck.easystock.data.model

import ch.mathorck.easystock.enums.article.ItemStatus

object ItemStatusSerializer : DbEnumSerializer<ItemStatus>("ItemStatus", ItemStatus.entries.toTypedArray())
