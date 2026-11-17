package ch.mathorck.easystock.exceptions.categories

/** The category was deleted (or never existed). */
class CategoryNotFoundException(val id: String) : IllegalStateException("No active category: $id")
