package ch.mathorck.easystock.exceptions.categories

/** A deleted category still holds this name (the unique index covers it). */
class DeletedCategoryNameException(val name: String) : IllegalStateException("Name of a deleted category: $name")
