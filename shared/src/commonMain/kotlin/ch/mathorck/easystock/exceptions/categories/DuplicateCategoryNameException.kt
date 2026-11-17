package ch.mathorck.easystock.exceptions.categories

/** An active category already has this name (ignoring case). */
class DuplicateCategoryNameException(val name: String) : IllegalStateException("Category name already used: $name")
