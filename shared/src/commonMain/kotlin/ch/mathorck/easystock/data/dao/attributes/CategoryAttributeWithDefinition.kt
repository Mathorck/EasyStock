package ch.mathorck.easystock.data.dao.attributes

import androidx.room.Embedded
import androidx.room.Relation
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.attributes.CategoryAttribute

/** An attribute linked to a category, with its definition. */
data class CategoryAttributeWithDefinition(
    @Embedded val link: CategoryAttribute,
    @Relation(parentColumn = "attribute_id", entityColumn = "id")
    val definition: AttributeDefinition,
)
