package ch.mathorck.easystock.data.model

import ch.mathorck.easystock.enums.attributes.AttributeDataType

object AttributeDataTypeSerializer : DbEnumSerializer<AttributeDataType>("AttributeDataType", AttributeDataType.entries.toTypedArray())
