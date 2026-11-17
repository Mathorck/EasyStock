package ch.mathorck.easystock.data.dao.attributes

/** One wanted link of a category, see [AttributeDao.setCategoryAttributes]. */
data class CategoryAttributeInput(val attributeId: String, val required: Boolean)
